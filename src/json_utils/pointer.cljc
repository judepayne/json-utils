(ns json-utils.pointer
  (:require [clojure.string     :as str]
            [json-utils.util    :as util :refer [promise?]]
            [json-utils.uri     :as uri]
   #?(:cljs [promesa.core       :as p]))
  (:refer-clojure :exclude [resolve])
  #?(:cljs (:require-macros [json-utils.util])))


;; 1. ------------------ resolve/ deref a pointer ---------------


(def ^:private err "uri must be a link to a valid json doc.")

(defn res-type
  [& args]
  (let [[ptr maybe-json] args]
    (let [dispatch
          (cond
            (nil? maybe-json)     :ptr
            :else                 :default)]
      dispatch)))


(defmulti res* res-type) 

(defmethod res* :ptr
  [ptr] (let [parts (if (string? ptr)
                      (uri/uri-parts ptr)
                      ptr)]
          (if (not (uri/external-uri? (first parts)))
            (util/exception err)
            (try
              #?(:clj (res* (rest parts) (uri/resolve-external-url (first parts)))
                :cljs  (let [p (uri/resolve-external-url (first parts))]
                         (if (util/promise? p)
                           (-> (p/resolved p)
                               (p/then (fn [x] (res* (rest parts) x))))
                           (res* (rest parts) p))))
              #?(:clj  (catch Exception e (util/exception err))
                 :cljs (catch js/Error e (util/exception err)))))))
  
(defmethod res* :default
  [ptr json]
  (if (or (nil? ptr) (empty? ptr))
    json     ;; return quick if nothing to do
    (let [ptr  (if (string? ptr) (uri/uri-parts ptr) ptr)]
      (reduce (fn [acc cur]
               (let [cur (if (string? cur) (keyword cur) cur)]
                 (get acc cur)))
             json
             ptr))))

;; CLJS note
;; Since in cljs land, we have to work with promises, resolving a pointer which points
;; to an external url will result in a js promise (that eventually resolves to the data).
;; We can work around this using standard techniques. e.g.:

(comment
  (def A (atom nil))

  (let [result (res* "http://json-schema.org/draft-07/schema#/title")]
    (when (util/promise? result)
      (-> (p/resolved result)
          (p/then (fn [x] (reset! A x))))))

  @A
)

;; Note: Resolved external url's are cached in the uri namespace, in a ttl cache
;; so we don't *always* get back a promise in cljs, sometimes the result is directly returned.
;; you can check if you have a promise or not using the promise? function in the util namespace.




;; ---------------------- resolve api ----------------------


(defn resolve
  "Resolves either a JSON pointer on its own (if its first part points to a json doc)
   or a JSON pointer within a supplied JSON document.
   The JSON may be supplied as a uri (which is resolved), the json as a string or a nested
   Clojure data structure representation of the json."
  ([ptr] (res* ptr))
  ([json ptr]
   (res* json ptr)))

;; 2 --------------------- lookup ---------------------------

;; TODO change these to keyword arguments.
(defn- lookup*
  ([form node] (lookup* form node = []))
  ([form node match-fn] (lookup* form node match-fn []))
  ([form node match-fn path]
   (cond
     (sequential? form)      (if (match-fn node form)
                               path
                               (->> (map-indexed
                                     (fn [idx item]
                                       (lookup* item node match-fn (conj path idx)))
                                     form)
                                    (mapcat identity)))

     (map? form)             (if (match-fn node form)
                               path
                               (->> (mapv (fn [[idx v]]
                                            (lookup* v node match-fn (conj path idx)))
                                          form)
                                    (mapcat identity)))

     :else                   (if (match-fn node form)
                               path
                               nil))))


;; 3. ------------------- lookup api ------------------------


(defn lookup
  ([form node] (lookup form node = []))
  ([form node match-fn] (lookup form node match-fn []))
  ([form node match-fn path]
   (let [str-form? (and (string? form) (string? node))
         form (if (string? form) (util/json->clj form) form)
         node (if (string? node) (util/json->clj node) node)]
     (case str-form?
       true          (let [res (lookup* form node match-fn path)]
                       (apply str "/" (interpose "/" (map name res))))
       false         (let [res (lookup* form node match-fn path)]
                       (if (empty? res) nil res))))))
