(ns email-scraper.core
 (:require 
   [clojure-mail.core :as mail]
   [email-scraper.db :as db]
   [email-scraper.helpers :refer :all]
   [clojure.core.async :as async :refer [<! >! <!! >!! timeout chan alt! alts!! go thread go-loop close!]]
   [clojure-mail.events :as events]
   [clojure-mail.core :refer :all]
   [clojure-mail.folder :refer (get-messages)]
   [clojure-mail.message :refer (read-message uid)])
 (:import [java.util Properties]))

(defn search-inbox-since [since]
  (let [sess (mail/get-session "imaps")
        s (mail/store "imaps" sess "mail.company.com" "devscraper@company.com" "password")
        folder (mail/open-folder s "inbox" :readonly)
        results (all-messages s "inbox" :since-uid since)]
      (reverse results))) ; process them in cronological order


(defn read-message-uid [message]
  {:message (read-message message) :uid (uid message)})

(def process (comp db/make-insert-map read-message));

; fn to handle message(s) received
(defn recieved [{messages :messages}]
  ; ignore the message and instead use a uid
  ; sometimes (a lot) our vm sql refuses to save stuff
  (try 
    (let [messages-from (search-inbox-since (+ (get-last-message-uid) 1))  
          parsed (take 100 (map process messages-from))
          last-uid (-> (take 100 messages-from) last uid)]  
      (db/save-messages parsed last-uid))
    (catch Exception e
      (clojure.pprint/pprint e))))

;start a listening session
(defn start-manager []
    (let [sess (mail/get-session "imaps")
          mail-store (mail/store "imaps" sess "mail.company.com" "devscraper@company.com" "password")
          folder (mail/open-folder mail-store "inbox" :readonly)
          im (events/new-idle-manager sess)]

        (events/add-message-count-listener recieved nil folder im)
      im))


(defn start-listening []
  ; loop forever restarting manager every 15 minutes
  (go-loop []
    (try 
      (spit (str path "log.txt") "Starting mail listener\n" :append true)
      (let [manager (start-manager)]
        ; hold 15 minutes beofre restarting
        (<! (timeout (* 60 1000 15))) 
        (events/stop manager))
      (catch Exception e 
        (clojure.pprint/pprint e)
        ; if we failed to login just stutter and retry
        (spit (str path "log.txt") "\n" :append true)
        (spit (str path "log.txt") e :append true)
        (spit (str path "log.txt") "\n" :append true)      
        (timeout 10)))
    (recur)))
(start-listening)
(print ".")
