(ns evolved-joshua.core
  (:use [twitter.oauth :as twitter-oauth]
        [twitter.callbacks]
        [twitter.callbacks.handlers]
        [twitter.api.streaming])
  (:require [clojure.set :as s]
            [clojure.core :as c]
            [clojure.string :as str]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [twitter.api.restful :as twitter]
            [overtone.at-at :as overtone]
            [evolved-joshua.logging :as logging]
            [environ.core :refer [env]]))

(def twitter-credentials (twitter-oauth/make-oauth-creds (env :app-consumer-key)
                                                         (env :app-consumer-secret)
                                                         (env :user-access-token)
                                                         (env :user-access-secret)))

(def botname "@josh_knoerzia")
(def hashtag "#evolvedJoshua")
;(def hashtag-self-dialog "#selfDialogEvolvedJoshua")
(def answer (env :answer))
(def message (str "I have evolved! The answer has been found. Ask me anything using " hashtag))

(def pool (overtone/mk-pool))

; atoms
(def answerFound (atom true))
(def last-tweet-id (atom 0))
(def user-map (atom {}))

;(def last-tweet-id-self-dialog (atom 0))
;(def last-tweet-text-self-dialog (atom (str "Hello. Say something random. " hashtag-self-dialog))) ;start string for self dialog.

(defn log-tweet-info [tweet]
  (logging/log (str "Tweet-ID: " (get-in tweet [:id])
                    "\n          Tweet:    " (get-in tweet [:text])
                    "\n          Answer:   " answer
                    "\n          Solved:   " @answerFound)))

(defn log-info-running-bot [tweet-id user question cs-old cs clever_ouput]
  (logging/log (str "Tweet-ID: " tweet-id
                    "\n          Tweet:    " question
                    "\n          User:     " user
                    "\n          CS-old:   " cs-old
                    "\n          CS:       " cs
                    "\n          Answer:   " clever_ouput)))

;; get fields from answer like this:
;;(let [{:strs [cs conversation_id clever_ouput ...]} (get-cleverbot-answer [question conversation-id])])
(defn get-cleverbot-answer [question cs]
  (json/read-str (str/replace (get-in (client/get (str "http://www.cleverbot.com/getreply?key=" (env :credentials-chatbot) "&input=" question "&cs=" cs)) [:body]) ":" " ")))

;; get chuck norris joke
(defn get-chucknorris-joke []
  (let  [{:strs [value]} (json/read-str (get-in (client/get "https://api.chucknorris.io/jokes/random") [:body]))]
    value))

;;get statuses with botname or hashtag
(defn get-tweets [searchterm]
  (get-in (twitter/search-tweets :oauth-creds twitter-credentials :params {:q searchterm :since_id @last-tweet-id}) [:body :statuses]))

;;tweeting to other user
(defn tweeting
  ([text]
   (when (and (not-empty text) (<= (count text) 140))
     (try
       (twitter/statuses-update :oauth-creds twitter-credentials :params {:status text})
       (logging/log (str "TWEETED: '" text "'"))
       (catch Exception e (println "Something went wrong: " (.getMessage e))))))
  ([text tweet-id]
   (when (and (not-empty text) (<= (count text) 140))
     (try
       (twitter/statuses-update :oauth-creds twitter-credentials :params {:status text :in_reply_to_status_id tweet-id})
       (logging/log (str "TWEETED TO: " tweet-id ": '" text "'"))
       (catch
         Exception e (println "Something went wrong: " (.getMessage e))
         (tweeting "A problem ocurred while tweeting. Seems Answertext was too long or non given." tweet-id))))))


;;checks tweet for answer
;;if true tweets and switches to run-bot
(defn check-answer [tweet]
  (let [text (get-in tweet [:text])
        tweet-id (get-in tweet [:id])]
    (when (.contains (str/lower-case text) (str/lower-case answer))
      (swap! answerFound (fn [x] true))
      (tweeting (str "@" (get-in tweet [:user :screen_name]) " " message) tweet-id))
    (log-tweet-info tweet)))

;;
(defn run-bot [tweet]
  (if (not (.contains (get-in tweet [:text]) message)) ;check if it is not the answertext from the bot itself
    (let [user (get-in tweet [:user :screen_name])
          question (str/replace (str/lower-case (get-in tweet [:text])) hashtag "")
          cs-old (get-in @user-map [(keyword user) :cs])
          tweet-id (get-in tweet [:id])
          {:strs [cs clever_output]} (get-cleverbot-answer question cs-old)]
      (log-info-running-bot tweet-id user question cs-old cs clever_output)
      (swap! user-map (fn [x] (update-in x [(keyword user) :cs] (fn [y] cs)))) ;update cs from user
      (tweeting clever_output tweet-id))))

;;parses through new tweets
(defn parse-tweets []
  (logging/log "PARSING")
  (let [tweets (if (= @answerFound true) (get-tweets hashtag) (get-tweets botname))]
    ;(logging (str tweets))
    (if (not (empty? tweets))
      (doseq [tweet tweets]
        (swap! last-tweet-id (fn [old-id] (if (> old-id (get-in tweet [:id])) old-id (get-in tweet [:id])))) ;update most recent tweet-id
        (if (= @answerFound true) (run-bot tweet) (check-answer tweet))))))

;;cleverbot talking with itself
;(defn self-dialog []
; (logging "###SELF-DIALOG")
; (let [tweet (first (get-tweets hashtag-self-dialog))]
;   (if (= tweet nil)
;     (tweeting @last-tweet-text-self-dialog)
;     (let [user (get-in tweet [:user :screen_name])
;           question (str/replace (str/lower-case (get-in tweet [:text])) hashtag-self-dialog "")
;           cs-old (get-in @user-map [(keyword user) :cs])
;           {:strs [cs clever_output]} (get-cleverbot-answer question cs-old)]
;       (swap! last-tweet-id-self-dialog (fn [x] (get-in tweet [:id])))
;       (swap! last-tweet-text-self-dialog (fn [x] clever_output))
;       (print-info-running-bot user question cs-old cs clever_output)
;       (swap! user-map (fn [x] (update-in x [(keyword user) :cs] (fn [y] cs)))) ;update cs from user
;       (tweeting @last-tweet-text-self-dialog @last-tweet-id-self-dialog)))))

(defn main []
  (overtone/every 20000 #(parse-tweets) pool)) ;every 30sec check if someone wrote #evolvedJoshua
  ;(overtone/every (* 60 60 1000) #(tweeting (get-chucknorris-joke)) pool)) ;every hour chuck norris joke
