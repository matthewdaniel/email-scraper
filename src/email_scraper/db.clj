(ns email-scraper.db
  (:require
    [email-scraper.helpers :refer :all]
    [honeysql.core :as sql]
    [honeysql.helpers :refer :all]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as j]
    [clojure.string :as str]))

(def db {:classname "net.sourceforge.jtds.jdbc.Driver"
         :subprotocol "jtds:sqlserver"
         :host "sqlserver"
         :user "root"
         :password "password"
         :subname "//sqlserver;MultipleActiveResultSets=True;databaseName=WebBlnnet"})
    
(defn- date-to-string [date] (.format (java.text.SimpleDateFormat. "MM/dd/yyyy hh:mm:ss") date))

; turn java datetime to string fn
(defn- json-val-parser [key value]
    (if (str/starts-with? key ":date-")
        (date-to-string value)
        value))

    
(defn save-message [{message :message uid :uid}]
    (spit (str path "log.txt") (str "received: " (:subject message) "\n") :append true)
    
    ; insert into db
    (try 
     (j/insert! db :brokerenabled.dbo.EventLog
        {:type "RawEmail"
         :depth 0
         :processed 0
         :triggerSource "EmailScraper"
         :name (:subject message)
         :category "Error Email"
         :details (json/write-str { :from (:address (first (:from message)))
                                    :body (:body (:body message))})
         :isFailed 1
         :date (date-to-string (:date-sent message))})
     (catch Exception e (spit (str path "log.txt")) e :append true))
    message)

(defn save-messages [messages last-uid]
    (clojure.pprint/pprint "saving")
    (try
      (j/insert-multi! db :brokerenabled.dbo.EventLog messages)
      (save-last-message-uid last-uid)
      (clojure.pprint/pprint "saved?")
     (catch Exception e 
        (clojure.pprint/pprint e)
        (throw e))))
        
(defn make-insert-map [message]
    (try
        {:type "RawEmail"
         :depth 0
         :processed 0
         :triggerSource "EmailScraper"
         :name (str (:subject message))
         :category "Error Email"
         :details (json/write-str { :from (:address (first (:from message)))
                                    :body (:body (:body message))})
         :isFailed 1
         :date (date-to-string (:date-sent message))}
      (catch Exception e 
            (spit (str path "log.txt")) e :append true
            (throw e))))

