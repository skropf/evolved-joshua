(ns evolved-joshua.logging
  (:require [clojure.string :as str]
            [clj-time.local :as tl]
            [clojure.java.io :as io]))

(defn log [text]
  (let [filename (str "logs/" (tl/format-local-time (tl/local-now) :basic-date) ".log")]
    (if (not (.exists (io/file "logs"))) (.mkdir (io/file "logs")))
    (spit filename (str "\n[" (tl/format-local-time (tl/local-now) :basic-time-no-ms) "] " text) :append true)))
