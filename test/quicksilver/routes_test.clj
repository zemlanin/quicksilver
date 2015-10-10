(ns quicksilver.routes-test
  (:require [clojure.test :refer :all]
            [quicksilver.routes :refer :all]))

(deftest absolute-paramless
  (testing "(absolute url)"
    (is (= "http://localhost:8080/" (absolute "/")))
    (is (= "http://localhost:8080/test" (absolute "/test")))
    (is (= "ws://localhost:8080/ws/ping" (absolute "/ws/ping")))))

(deftest absolute-params
  (testing "(absolute url & ps)"
    (is (= "http://localhost:8080/Zorro" (absolute "/:name" :name "Zorro")))
    (is (= "http://localhost:8080/12" (absolute "/:id" :id 12)))
))
