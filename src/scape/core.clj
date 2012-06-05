(ns scape.core
  (:require [datomic.api :refer [db q] :as d]
            [scape.emitter :refer [emit-transaction-data]]
            [scape.analyze :refer [analyze-file deep-dissoc]]
            [scape.schema :refer [schema]]
            [clojure.pprint :refer [pprint]]))


(comment
  (def uri "datomic:mem://ast")
  
  (d/delete-database uri)
  (d/create-database uri)
  
  (def conn (d/connect uri))
  
  (d/transact conn schema)
  
  (doseq [ast (analyze-file "cljs/core.cljs")]
    (let [tdata (emit-transaction-data ast)]
      (d/transact conn tdata)))
  
  ;; how many transactions? i.e., top level forms
  (count (analyze-file "cljs/core.cljs"))
  ;; 502
  
  ;; How many datoms is the above?
  (->> (analyze-file "cljs/core.cljs")
       (mapcat emit-transaction-data)
       count)
  ;; 146669 facts about cljs.core!
  
  ;; How many ast nodes are there in core.cljs?
  (count (q '[:find ?e
              :where
              [?e :ast/op]]
            (db conn)))
  
  ;; On what lines is the test part of an if statement a constant, and
  ;; what is that constant?
  (seq (q '[:find ?line ?form
            :where
            [?e :ast.if/test ?t]
            [?t :ast/op :ast.op/constant]
            [?t :ast/form ?form]
            [?t :ast/line ?line]]
          (db conn)))
  
  ;; What form is on line 291?
  (q '[:find ?form
       :where
       [?op :ast/op]
       [?op :ast/line 291]
       [?op :ast/form ?form]]
     (db conn))
  
  ;; Find documentation and line number
  (q '[:find ?line ?doc
       :in $ ?name
       :where
       [?def :ast/name ?name]
       [?def :ast.def/doc ?doc]
       [?def :ast/line ?line]]
     (db conn) "cljs.core.filter")
  
  ;; On what lines is the function 'map' used?
  (q '[:find ?line
       :in $ ?sym
       :where
       [?var :ast/op :ast.op/var]
       [?var :ast.var/local false]
       [?var :ast/form ?sym]
       [?var :ast/line ?line]]
     (db conn) "map")
  
  ;; What are the most used local/var names?
  (->>  (q '[:find ?var ?sym
             :in $ ?local
             :where
             [?var :ast.var/local ?local]
             [?var :ast/form ?sym]]
           (db conn) false)
        (map second)
        frequencies
        (sort-by second)
        reverse)
  
  ;; On what line is the return of a function method a constant and
  ;; what is the type of that constant?
  (q '[:find ?line ?type
       :where
       ;;[?fn :ast/op :ast.op/fn]
       [?_ :ast.fn/method ?fnm]
       [?fnm :ast/ret ?ret]
       [?ret :ast/op :ast.op/constant]
       [?ret :ast.constant/type ?type]
       [?ret :ast/line ?line]]
     (db conn))
  
  ;; Most used op's. Can this be combined into one query?
  (sort-by second
           (for [[op] (q '[:find ?op
                           :where
                           [?_ :ast/op ?op*]
                           [?op* :db/ident ?op]]
                         (db conn))]
             [op (count (q '[:find ?e
                             :in $ ?op
                             :where
                             [?e :ast/op ?op]]
                           (db conn) op))]))

  ;; On what lines is a loop used?
  (q '[:find ?line
       :where
       [?let :ast/op :ast.op/let]
       [?let :ast.let/loop true]
       [?let :ast/line ?line]]
     (db conn))
  
  )