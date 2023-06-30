(ns trip-planner.config
  (:require    [environ.core :refer [env]]))

(def token (env :telegram-token))