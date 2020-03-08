(ns json-utils.util
  (:require #?(:clj [clojure.data.json    :as json])
            [promesa.core                 :as p]))


(defn exception
  [msg]
  #?(:clj (throw (Exception. msg)) 
     :cljs (throw (js/Error. msg))))


(defn clj->json
  [form]
  #?(:clj  (json/write-str form)
     :cljs (.stringify js/JSON (clj->js form))))


(defn json->clj
  [js]
  #?(:clj  (json/read-str js :key-fn keyword)
     :cljs (js->clj (.parse js/JSON js) :keywordize-keys true)))


#?(:clj
   (defmacro myslurp [file]
     (clojure.core/slurp file)))


(defn promise?
  [maybe-prom]
  (p/promise? maybe-prom))


(defn cljs-env?
  "Take the &env from a macro, and tell whether we are expanding into cljs."
  [env]
  (boolean (:ns env)))


#?(:clj
   (defmacro if-cljs
     "Return then if we are generating cljs code and else for Clojure code.
      https://groups.google.com/d/msg/clojurescript/iBY5HaQda4A/w1lAQi9_AwsJ"
     [then else]
     (if (cljs-env? &env) then else)))


#?(:clj
   (defmacro try-raise-clj
     "Simplify try catch expression that just raise a new exception with error-msg."
     [error-msg & forms]
     `(try ~@forms
           (catch Exception ~(gensym 'e) (exception ~error-msg)))))


#?(:clj
   (defmacro try-raise-cljs
     "Simplify try catch expression that just raise a new exception with error-msg."
     [error-msg & forms]
     `(try ~@forms
           (catch js/Error ~(gensym 'e) (exception ~error-msg)))))
