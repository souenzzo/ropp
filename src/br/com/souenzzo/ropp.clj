(ns br.com.souenzzo.ropp
  (:require [clojure.string :as string]
            [io.pedestal.http.route :as route]
            [com.wsscode.pathom3.interface.eql :as p.eql]
            [io.pedestal.http.body-params :as body-params]
            [cheshire.core :as json]
            [br.com.souenzzo.ropp.json-schema :as json-schema]
            [com.wsscode.pathom3.connect.operation :as pco]
            [clojure.spec.alpha :as s]
            [com.wsscode.pathom3.connect.indexes :as pci]))

(s/def ::query-params map?)
(s/def ::header-params map?)
(s/def ::path-params map?)
(s/def ::cookie-params map?)
(s/def ::body-params any?)
(s/def ::operation->ident ifn?)
(s/def ::open-api (s/map-of string? any?))

(defn params
  [{{:strs [content-type]} :headers
    {:strs [content]}      "requestBody"
    :strs                  [parameters]
    :as                    req}]
  (transduce
    (keep (fn [{:strs [name in] :as spec}]
            (let [[path target] ({"query"  [[:query-params (keyword name)]
                                            ::query-params]
                                  "header" [[:headers name]
                                            ::header-params]
                                  "path"   [[:query-params (keyword name)]
                                            ::path-params]
                                  ;; TODO
                                  #_#_"cookie" [[:header cookies]
                                                ::cookie-params]}
                                 in)
                  v (get-in req path)]
              (when-not (nil? v)
                [target (keyword name) (json-schema/parse req spec v)]))))
    (fn
      ([] {})
      ([result] result)
      ([result [target ident value]]
       (assoc-in result [target ident] value)))
    (assoc
      (if-let [spec (get content content-type)]
        {::body-params (json-schema/parse req spec
                         (get req ({"application/json" :json-params} content-type)))}
        {})
      ::query-params {}
      ::header-params {}
      ::cookie-params {}
      ::path-params {})
    parameters))

(s/fdef params
  :args (s/cat :request map?))

(defn operations
  [{:strs [paths]}]
  (for [[path routes] paths
        [method {:strs [operationId]
                 :as   operation}] routes
        :when (map? operation)
        :let [operation-id (or operationId
                             [path method])]]
    (assoc operation
      ::operation-id operation-id
      ::path path
      ::method method)))


(defn expand-routes
  [{::keys [operation->ident open-api]
    :as    env}]
  (let [raw-routes (for [{::keys [operation-id path method]
                          :as    operation} (operations open-api)
                         :let [ident (operation->ident operation-id)]
                         :when ident]
                     [(-> path
                        (string/replace #"\{" ":")
                        (string/replace #"\}" ""))
                      (keyword (string/lower-case method))
                      (into []
                        (remove nil?)
                        [{:name  ::on-error
                          :error (fn [ctx ex]
                                   (let [{::keys [on-exception]} (p.eql/process env
                                                                   {::ex ex}
                                                                   [::on-exception])]
                                     (if on-exception
                                       (assoc ctx :response on-exception)
                                       ctx)))}
                         (when (contains? operation "requestBody")
                           (body-params/body-params))
                         {:name  ::with-openapi
                          :enter (fn [ctx]
                                   (update ctx
                                     :request merge env open-api operation))}
                         {:name  ::tx-build
                          :enter (fn [{:keys [request]
                                       :as   ctx}]
                                   (let [read? (keyword? ident)
                                         params (params request)
                                         tx (if read?
                                              `[~ident]
                                              `[(~ident ~params)])
                                         path [ident]
                                         req (cond->
                                               (assoc request
                                                 ::tx tx
                                                 ::path path)
                                               read? (assoc ::input params))]

                                     (assoc ctx :request req)))}
                         (fn [{::keys [tx path input]
                               :as    req}]
                           (-> (if input
                                 (p.eql/process req input tx)
                                 (p.eql/process req tx))
                             (get-in path)))])
                      :route-name (keyword ident)])]
    (-> raw-routes
      set
      route/expand-routes)))

(s/fdef expand-routes
  :args (s/cat :service-map (s/keys :req [::operation->ident ::open-api])))


(defn explain-data
  [{::keys     [operation->ident open-api]
    ::pci/keys [index-attributes
                index-mutations]
    :as        env}]
  (let [ops (operations open-api)]
    (concat
      (for [{::keys [operation-id]} ops
            :when (not (ident? (operation->ident operation-id)))]
        {:msg (str "Can't find " (pr-str operation-id) " in operation->ident")})
      (for [{::keys [operation-id]} ops
            :let [ident (operation->ident operation-id)]
            :when (and (not (contains? index-attributes ident))
                    (not (contains? index-mutations ident)))]
        {:msg (str "Can't find " (pr-str ident) " in pathom register")}))))
