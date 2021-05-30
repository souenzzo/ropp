(ns br.com.souenzzo.ropp.json-schema
  (:require [clojure.string :as string]))

(defmulti -parse-type
  "
  Specification Docs:
  https://swagger.io/docs/specification/data-models/data-types/
  "
  (fn [{:strs [type]} _ _] type))

(defmethod -parse-type "string"
  [_ _ _])

(defmethod -parse-type "number"
  [_ _ _])

(defmethod -parse-type "integer"
  [_ _ m]
  (Long/parseLong m))

(defmethod -parse-type "boolean"
  [_ _ _])

(defmethod -parse-type "array"
  [_ _ _])

(defmethod -parse-type "object"
  [{:strs [required properties]} spec v]
  v)

(defmethod -parse-type :default
  [{:strs [$ref]} spec v]
  (-parse-type (get-in spec
                 (rest (string/split $ref #"/")))
    spec v))

(defn parse
  [global {:strs [default required schema]} value]
  (let [[x ex] (try
                 [(-parse-type schema global value)]
                 (catch Throwable ex
                   [nil ex]))]
    x))
