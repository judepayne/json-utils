(ns json-utils.uri
  (:require [json-utils.util                :as util]
            [clojure.string                 :as str]
           #?(:clj  [clojure.core.cache     :as cache]
              :cljs [cljs.cache             :as cache])
           #?(:cljs [promesa.core           :as p])
           #?(:cljs [httpurr.status         :as s])
           #?(:cljs [httpurr.client.xhr     :as xhr]))
  (:refer-clojure :exclude [uri? uri]))


(defn esc
  [s]
  (-> s
      (str/replace #"~" "~0")
      (str/replace #"/" "~1")))


(defn unesc
  [s]
  (-> s
      (str/replace #"~0" "~")
      (str/replace #"~1" "/")))


(def external-uri-reg #"https?://(www\.)?[-a-zA-Z0-9@:%._\+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_\+.~#?&//=]*)")


(def fragment-reg #"#(.+)")


(def uri-part-reg #"^(.+)#")


(defn external-uri?
  "Return true if s is a uri."
  [s]
  (if (and (string? s) (re-matches external-uri-reg s)) true false))


(def ^:private internal-uri #"^/.*#$")


(defn internal-uri?
  "Returns true if s is an internal uri."
  [s]
  (if (and (string? s) (re-matches internal-uri s)) true false))


(defn uri?
  [s]
  (or (external-uri? s) (internal-uri? s)))


(defn uri-part
  [s]
  (first (re-find uri-part-reg s)))


(defn fragment-part
  [s]
  (second (re-find fragment-reg s)))


(defn esc-uri-in-token
  [s]
  (let [u (uri-part s)]
    (if u
      (let [l1 (count u)
            l2 (count s)
            end (subs s l1 l2)
            u2 (esc u)]
        (str u2 end))
      s)))


(defn- parse-int [s]
  (try
    #?(:clj (Integer/parseInt s)
       :cljs (js/parseInt s))
    #?(:clj (catch Exception e s)
       :cljs (catch js/Error e s))))


(defn parts->clj [parts]
  (reduce
   (fn [acc cur]
     (let [n (parse-int cur)]
       (cond
         (or (= cur "#") (= cur ""))         acc
         (or (integer? cur) (keyword? cur))  (conj acc cur)
         (integer? n)                        (conj acc n)
         :else                               (conj acc (keyword cur)))))
   []
   parts))


(defn uri-parts
  [s]
  (let [s (unesc s)]
    (if (not (external-uri? s))
      (parts->clj (str/split s #"/"))
      (let [ext (uri-part s)
            ext' (if (nil? ext) s ext)
            frags? (> (count s) (count ext'))]
        (if frags?
          (cons ext' (parts->clj (rest (str/split (fragment-part s) #"/"))))
          [ext'])))))


(def C (atom (cache/ttl-cache-factory {} :ttl 60000)))


#?(:clj
   (defn ttl
     "Sets how long (in miliseconds) external url's are cached for."
     [cache-for]
     (alter-var-root
      #'C
      (fn [at]
        (atom (cache/ttl-cache-factory @at cache-for))))))


#?(:cljs
   (defn slurp [url res-fn]
     (-> (xhr/get url)
         (p/then res-fn))))


(defn- memo-slurp
  [url]
  #?(:clj 
     (let [cached (get @C url)]
       (if cached
         cached
         (let [res (util/json->clj (slurp url))]
           (println url)
           (swap! C assoc url res)
           res)))
     :cljs
     (let [cached (get @C url)]
       (if cached
         cached
         (slurp url
                (fn [x] (condp = (:status x)
                          s/ok
                          (p/resolved
                           (let [res (util/json->clj (:body x))]
                             (swap! C assoc url res)
                             res))
                          
                          (or s/not-found s/unauthorized)
                          (p/rejected url))))))))


(defn resolve-external-url
  "Resolves a uri to a result."
  [url] 
  #?(:clj
     (if (not (external-uri? url))
       (util/exception (str url " does not appear to be a valid url."))
       (try
         (memo-slurp url)
         (catch Exception e (try
                              (memo-slurp (str "http://" url))
                              (catch Exception e (util/exception
                                                  (str url " cannot be resolved.")))))))
     :cljs
     (if (not (external-uri? url))
       (util/exception (str url " does not appear to be a valid url."))
       (try
         (memo-slurp url)
         (catch js/Error e (try
                             (memo-slurp (str "http://" url))
                             (catch js/Error e (util/exception
                                                (str url " cannot be resolved.")))))))))
