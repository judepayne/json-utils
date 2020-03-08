(ns json-utils.traversal
  (:require [json-utils.uri     :as uri]
            [json-utils.util    :as util]
            [json-utils.pointer :as ptr]
            [clojure.walk       :as w]
   #?(:cljs [promesa.core       :as p]))
  (:refer-clojure :exclude [find replace]))


;; ---------------stateful traversal----------------


;; -- replace-fn and state-fn required to support with-path
(defn kv-external-uri?
  [form]
  (and
   (map-entry? form)
   (string? (val form))
   (let [u (uri/uri-part (val form))]
     (and (not (nil? u)) (uri/external-uri? u)))))


(defn replace [dont-expand expand-levels]
  "Returns a replacement function (accepting state and a form) that merges
   :$path (when the form is a map) and resolves uris in the val of a map entry."
  (let [expansions (atom #{})]
    (fn [state form]
      (cond
        (and (kv-external-uri? form)
             (not (contains? dont-expand (key form)))
             (not (contains? @expansions form))
             (<= (count state) expand-levels))
        
        (if-let [ex (ptr/resolve (val form))]
          (do
            (swap! expansions conj form)
            [(key form) ex]))

        (map? form)    (merge {:$path state} form)
        :else form))))


(defn vec? [x] (and (sequential? x) (not (map-entry? x))))


(defn update-st [state parent form]
  "Returns a new state when passed the previous state, a form and its parent."
  (cond
    (map-entry? form)                                 (conj state (key form))
    (and (or (map? form) (vec? form)) (vec? parent))  (conj state (.indexOf parent form))
    :else state))


(defn json-branch?
  "True if the form is a branch in a json data structure."
  [form]
  (or (map? form) (vec? form) (map-entry? form)))


(defn json-children
  "Returns the children of the (json) form."
  [form]
  (cond
    (map? form)          (into [] form)
    (vec? form)          form
    (map-entry? form)    (if (coll? (val form)) (val form) [(val form)])
    :else                nil))


;; ---------------- traversal api ---------------------


(defn prewalk
  "Like clojure.walk/prewalk but stateful.
   parent is the parent of the form. state is recursed down through the form,
   at each level updated by the state-fn (which must accept previous state, the
   parent form and the current form. replace-fn accepts the current state and form."
  ([parent state state-fn replace-fn form]
   (letfn [(pw [parent state form]
             (let [nxt-s (state-fn state parent form)
                   nxt-f (replace-fn nxt-s form)]
               (w/walk (partial pw form nxt-s) identity nxt-f)))]
     (pw parent state form))))


(defn with-path
  "Decorates a form with :path information at all levels.
   Excepts either clojure data structures or a json string.
   :expand-levls is an integer which determines to how many levels down into the form
   external uri's should (still) be expanded. The default is 0.
   :exceptions is a set of keywords not to be uri/resolved."
  [form & {:keys [expand-levels exceptions]
           :or {exceptions #{} expand-levels 0}}]
  (prewalk nil [] update-st (replace exceptions expand-levels) form))


(def json-tree-seq
  "Provides a tree-seq for json structures."
  (partial tree-seq json-branch? json-children))


#?(:cljs
   (defn with-path-promise
     "Same as with path but the first pass through the tree yielded any
      uncompleted promises, waits until those are resolved. cljs only.
      f is a completion/callback function to be applied to the fully
      resolved tree, e.g. to put it into some state, for example an Atom."
     [form f & {:keys [expand-levels exceptions]
                :or {exceptions #{} expand-levels 0}}]
     (let [tree (with-path form :expand-levels expand-levels :exceptions exceptions)
           proms (->> tree json-tree-seq (filter util/promise?))]
       (if (empty? proms)
         tree
         (let [p (p/all proms)]
           (p/then p (fn [_]
                       (with-path form
                         :expand-levels expand-levels
                         :exceptions exceptions))))))))
