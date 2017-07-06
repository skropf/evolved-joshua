(ns evolved-joshua.core
  (:use [twitter.oauth :as twitter-oauth]
        [twitter.callbacks]
        [twitter.callbacks.handlers]
        [twitter.api.streaming])
  (:require [clojure.set :as s]
            [clojure.core :as c]
            [clojure.string :as str]
            [clj-http.client :as client]
            [twitter.api.restful :as twitter]
            [overtone.at-at :as overtone]
            [environ.core :refer [env]]))

(def twitter-credentials (twitter-oauth/make-oauth-creds (env :app-consumer-key)
                                                         (env :app-consumer-secret)
                                                         (env :user-access-token)
                                                         (env :user-access-secret)))
(def cleverbot-credentials (env :credentials-chatbot))
(def botname "@josh_knoerzia")
(def hashtag "#joshua_test")
(def answer "testanswer")
(def message "...") ;"I have become! The answer has been found. I have evolved! Now I am an awakened multidimensional being. Ask me anything using")
(def last-tweet-id 0)
(def pool (overtone/mk-pool))
(def answerFoundAtom (atom 0))
(def user_map {})

;;(defn tweet-text []
;;  (let [tweet
;;    (if (<= (count tweet) 140)
;;    tweet
;;    (recur))]))
(defn contains-every? [m keyseqs]
  (let [not-found (Object.)]
    (not-any? #{not-found}
              (for [ks keyseqs]
                (get-in m ks not-found)))))

;;gets a new session-id from cleverbot
(defn get-new-api-session-id []
  (second (str/split (first (str/split (str/replace (get-in (client/get (str "https://www.cleverbot.com/getreply?key=" cleverbot-credentials)) [:body]) "\"" "") #",")) #":")))

;;gets answer from cleverbot
(defn get-answer [question user]
  (println (client/get (str "http://www.cleverbot.com/getreply?key=CC2zcmlHKPv2CkXFUo944Kbuq3w&input=" question "&cs=" (user_map (keyword user)) {:accept :json}))))


;;get statuses with @botname
(defn get-statuses []
  (get-in (twitter/search-tweets :oauth-creds twitter-credentials :params {:q botname :since_id last-tweet-id}) [:body :statuses]))

;;tweeting
(defn tweet [text tweet-id]
  (when (and (not-empty text) (<= (count text) 140))
    (try
      (twitter/statuses-update :oauth-creds twitter-credentials :params {:status text :in_reply_to_status_id tweet-id})
      (println (str "###TWEETED TO: " tweet-id ": '" text "'"))
      (catch Exception e (println "Something went wrong: " (.getMessage e))))))


(defn parse-statuses [statuses]
  (if (= @answerFoundAtom 1)
    ((println "###IN parse-statuses")
     (println "some more prints to check not working code"));;RUNNING BOT - TBI

    ;;checks tweet for answer, if solved => RT with message
    ((println "###IN parse-statuses-for-answer")
     (doseq [s statuses]
       (def tweet-text (get-in s [:text]))
       (def user (get-in s [:user :screen_name]))
       (def tweet-id (get-in s [:id]))
       (def last-tweet-id tweet-id)
       (println (str "###Tweet-ID: " tweet-id "\nTweet: \"" tweet-text "\"" "\nAnswer: " answer "\nSolved: " (.contains  (str/lower-case tweet-text) answer) "\n"))
       (when (.contains  (str/lower-case tweet-text) answer)
        (swap! answerFoundAtom inc)
        (def text (str "@" user " " message " " botname))
        (println (str tweet-id " - " text))
        (tweet text tweet-id)
        (println "###Answer found! Switching to evolvedJoshua")
        (println @answerFoundAtom)
        (println "bot should be finished now"))))))

;;  (println "in parse statuses")
;;  (if (not (empty? statuses))
;;      (for [s statuses]
;;        ((def question (str/trim (str/replace (str/lower-case (get-in s [:text])) hashtag "")))
;;         ;;if not in map add user with ai-api-session-id
;;         (when (not (contains-every? user_map (get-in s [:user :screen_name])))
;;          (merge-with #(or %1 %2)
;;            user_map
;;            {(keyword (get-in s [:user :screen_name])) (str/replace (first (str/split (second (str/split (get-in (get-api-session-id) [:body]) #":")) #",")) "\"" "")}
;;         (println "user added in map"))))

(defn start[]
  (println "###Start")
  (parse-statuses (get-statuses))
  (println "bot did finish. end of parse"))


(defn main []
  (println "###Starting bot###")
  (overtone/every 5000 #(start) pool))



;;(main)

;   (Thread/sleep 30000))
; (status-update "")
; (while true
;   (parse-statuses (get-in (twitter/search-tweets :oauth-creds twitter-credentials
;                                                            :params {:q botname}) [:body :statuses]))
