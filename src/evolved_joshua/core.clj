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
(def hashtag "#joshua_test")
(def answer "testanswer")
(def message "...") ;"I have become! The answer has been found. I have evolved! Now I am an awakened multidimensional being. Ask me anything using")
(def last-tweet-id (atom 0))
(def pool (overtone/mk-pool))
(def answerFoundAtom (atom 0))
(def user_map {})


(defn contains-every? [m keyseqs]
  (let [not-found (Object.)]
    (not-any? #{not-found}
              (for [ks keyseqs]
                (get-in m ks not-found)))))

;; get fields from answer like this:
;;(let [{:strs [id1 id2 ...]} (get-cleverbot-answer [arg1 arg2])])
(defn get-cleverbot-answer [question conversation-id]
  (json/read-str (str/replace (get-in (client/get (str "http://www.cleverbot.com/getreply?key=" cleverbot-credentials "&input=" question "&cs=" conversation-id)) [:body]) ":" " ")))


;;get statuses with botname or hashtag
(defn get-statuses [searchterm]
  (get-in (twitter/search-tweets :oauth-creds twitter-credentials :params {:q searchterm :since_id last-tweet-id}) [:body :statuses]))

;;tweeting to other user
(defn tweet [text tweet-id]
  (println "###IN tweet")
  (when (and (not-empty text) (<= (count text) 140))
    (try
      (twitter/statuses-update :oauth-creds twitter-credentials :params {:status text :in_reply_to_status_id tweet-id})
      (println (str "TWEETED TO: " tweet-id ": '" text "'"))
      (catch Exception e (println "Something went wrong: " (.getMessage e))))))


(defn parse-statuses [statuses]
  (if (not (empty? statuses))
    (doseq [status statuses]
      (swap! last-tweet-id (fn [x] (get-in status [:id]))) ;update most recent tweet-id
      (if (= @answerFoundAtom 1) (run-bot status) (check-answer status)))))


(defn check-answer [status]
  (println (str "Tweet-ID: " (get-in status [:id])
               "\nTweet:  " (get-in status [:text])
               "\nAnswer: " answer
               "\nSolved: " (.contains  (str/lower-case (get-in status [:text])) answer)))
  (when (.contains (str/lower-case (get-in status [:text])) answer)
    (swap! answerFoundAtom (fn [x] 1))
    (def text (str "@" (get-in status [:user :screen_name]) " " message " " botname))
    (println (str (get-in status [:id]) " - " text))
    (tweet text (get-in status [:id]))
    (println "###Answer found! Switching to evolvedJoshua")
    (println @answerFoundAtom)
    (println "bot should be finished now")))

(defn run-bot [status])

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
  (parse-statuses (get-statuses botname))
  (println "bot did finish. end of parse"))


(defn main []
  (let [{:strs [cs conversation_id clever_output]} (get-cleverbot-answer "how are you?" "")]
    (println cs conversation_id clever_output)))

;(overtone/every 5000 #(start) pool))



;;(main)

;   (Thread/sleep 30000))
; (status-update "")
; (while true
;   (parse-statuses (get-in (twitter/search-tweets :oauth-creds twitter-credentials
;                                                            :params {:q botname}) [:body :statuses]))
