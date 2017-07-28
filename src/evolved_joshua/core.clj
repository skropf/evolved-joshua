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
            [environ.core :refer [env]]))

(def twitter-credentials (twitter-oauth/make-oauth-creds (env :app-consumer-key)
                                                         (env :app-consumer-secret)
                                                         (env :user-access-token)
                                                         (env :user-access-secret)))
(def cleverbot-credentials (env :credentials-chatbot))

(def botname "@josh_knoerzia")
(def hashtag "#testtag") ;"#evolvedJoshua")
(def answer "answer") ;"I have evolved!!!")
(def message (str "wohou answer answer found. ask me with " hashtag)) ;(str "I have evolved! The answer has been found. Ask me anything using " hashtag))

(def pool (overtone/mk-pool))

(def answerFound (atom false))
(def last-tweet-id (atom 0))
(def user-map (atom {}))

(defn print-tweet-info [tweet]
  (spit "log" (str "Tweet-ID: " (get-in tweet [:id])
                         "\nTweet:  " (get-in tweet [:text])
                         "\nAnswer: " answer
                         "\nSolved: " @answerFound)))

(defn print-info-running-bot [user question tweet-id cs-old cs clever_ouput]
  (spit "log" (str "Tweet-ID: " tweet-id
                         "\nTweet:  " question
                         "\nUser: " user
                         "\nCS-old: " cs-old
                         "\nCS: " cs
                         "\nAnswer: " clever_ouput)))

;; get fields from answer like this:
;;(let [{:strs [cs conversation_id clever_ouput ...]} (get-cleverbot-answer [question conversation-id])])
(defn get-cleverbot-answer [question cs]
  (json/read-str (str/replace (get-in (client/get (str "http://www.cleverbot.com/getreply?key=" cleverbot-credentials "&input=" question "&cs=" cs)) [:body]) ":" " ")))


;;get statuses with botname or hashtag
(defn get-tweets [searchterm]
  (get-in (twitter/search-tweets :oauth-creds twitter-credentials :params {:q searchterm :since_id last-tweet-id}) [:body :statuses]))

;;tweeting to other user
(defn tweeting [text tweet-id]
  (when (and (not-empty text) (<= (count text) 140))
    (try
      (twitter/statuses-update :oauth-creds twitter-credentials :params {:status text :in_reply_to_status_id tweet-id})
      (spit "log" (str "TWEETED TO: " tweet-id ": '" text "'"))
      (catch Exception e (println "Something went wrong: " (.getMessage e))))))

;;checks tweet for answer
;;if true tweets
(defn check-answer [tweet]
  (when (.contains (str/lower-case (get-in tweet [:text])) answer)
    (swap! answerFound (fn [x] true))
    (tweeting (str "@" (get-in tweet [:user :screen_name]) " " message) (get-in tweet [:id])))
  (print-tweet-info tweet))

;;TOIMPLEMENT
(defn run-bot [tweet]
  (let [user (get-in tweet [:user :screen_name])
        question (str/replace (str/lower-case (get-in tweet [:text])) hashtag "")
        tweet-id (get-in tweet [:id])
        cs-old (get-in @user-map [(keyword user) :cs])
        {:strs [cs clever_output]} (get-cleverbot-answer question cs-old)]
    (print-info-running-bot user question tweet-id cs-old cs clever_output)
    (swap! user-map (fn [x] (update-in x [(keyword user) :cs] (fn [y] cs)))) ;update cs from user
    (tweeting clever_output tweet-id)))

;;parses through new tweets
(defn parse-tweets []
  (spit "log""###PARSING")
  (def tweets (if (= @answerFound true) (get-tweets hashtag) (get-tweets botname)))
  (if (not (empty? tweets))
    (doseq [tweet tweets]
      (swap! last-tweet-id (fn [x] (get-in tweet [:id]))) ;update most recent tweet-id
      (if (= @answerFound true) (run-bot tweet) (check-answer tweet)))))


(defn start[]
  (overtone/every 5000 #(parse-tweets) pool))
