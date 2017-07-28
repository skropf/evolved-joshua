(defproject evolved-joshua "0.1.0-beta"
  :description "Conversational bot which uses twitter as interface and Cleverbot as answering AI"
  :url "https://github.com/skropf/evolved-joshua"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [proto-repl "0.3.1"]
                 [clj-http "3.6.1"]
                 [environ "1.0.0"]
                 [overtone/at-at "1.2.0"]
                 [twitter-api "0.7.8"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.slf4j/slf4j-log4j12 "1.7.12"]
                 [log4j/log4j "1.2.17"]]
   :main evolved-joshua.core
   :min-lein-version "2.0.0"
   :plugins [[lein-environ "1.0.0"]])
