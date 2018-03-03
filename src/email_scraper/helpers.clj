(ns email-scraper.helpers)
    
(def path "./")

(defn get-last-message-uid []
    (try 
     (read-string (slurp (str path "last-uid.txt")))
     (catch Exception e
       (clojure.pprint/pprint e)
       ; default to 1 since otherwise it will complain
       1)))
     
 (defn save-last-message-uid [uid]
     (spit (str path "last-uid.txt") uid))
 