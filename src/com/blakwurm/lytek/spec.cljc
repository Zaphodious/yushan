(ns com.blakwurm.lytek.spec
  "Contains the spec definitions for Lytek's data model, seperated
  by section. 
  
  The spec here serves the following purposes:
    1. Defining the data model for the Lytek library
    1. Human-accessable documentation for the shape of Lytek data
    1. Data 'shape' validation for API calls
    1. Data shape coersion for API calls
    1. Data generation for testing purposes

  In order to meet these goals, the specs in this file are bound by the following
  constraints:
    1. Any spec must be generatable by spec.gen *without* providing a custom generator
      + This will ensure that the specs used are generic and common
    1. Any spec must conform to data which is itself valid according go that spec.
      + Example- s/cat conforms to a map, which is not valid according to s/cat. Therefore,
        s/cat should not be used.
      + Example- s/tuple conforms to a vector, which is valid according to s/tuple. Therefore,
        s/tuple is acceptable.
    "  
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.set :as se]
            [clojure.string :as string]
            [spec-coerce.core :as sc]))

(defn named? [a] (or (string? a) (keyword? a) (symbol? a)))
(defn coerce-to-string [a]
  (if (named? a)
    (name a) (str a)))
(defn coerce-to-keyword [a]
  (keyword (coerce-to-string a)))

(def attribute-keys
  [:strength
   :dexterity
   :stamina
   :charisma
   :manipulation
   :appearance
   :perception
   :intelligence
   :wits])
(def attribute-strings
  (map name attribute-keys))

(def ability-keys
  [:archery
   :athletics
   :awareness
   :brawl
   :bureaucracy
   :craft
   :dodge
   :integrity
   :investigation
   :larceny
   :linguistics
   :lore
   :martial-arts
   :medicine
   :melee
   :occult
   :performance
   :presence
   :resistance
   :ride
   :sail
   :socialize
   :stealth
   :survival
   :thrown
   :war])
(def ability-strings
  (map name ability-keys))

(defn not-blank? [a] 
  (not (string/blank? a)))

(s/def :lytek/id
  (s/and string?
         not-blank?))
(sc/def :lytek/id coerce-to-string)

(s/def :lytek/name
  (s/and string?
         not-blank?))
(sc/def :lytek/name coerce-to-string)

(s/def :lytek/title
  string?)
(sc/def :lytek/title coerce-to-string)

(s/def :lytek/background
  string?)
(sc/def :lytek/background coerce-to-string)

(s/def :lytek/description
  string?)
(sc/def :lytek/description coerce-to-string)

(s/def :lytek/owner
  string?)
(sc/def :lytek/owner coerce-to-string)

(s/def :lytek/category
  #{:character
    :rulebook})
(sc/def :lytek/category coerce-to-keyword)

(def solar-castes
  #{:dawn
    :night
    :eclipse
    :twilight
    :zenith})

(def terrestrial-aspects
  #{:wood
    :air
    :water
    :fire
    :earth})

(s/def :lytek/subcategory
  (se/union solar-castes
            terrestrial-aspects))
(sc/def :lytek/subcategory coerce-to-keyword)

(s/def :lytek/rank  
  (s/int-in 0 6))

(s/def :lytek/rulebooks
  (s/coll-of :lytek/name :into [] :distinct true))

(s/def :lytek/attribute
  (set attribute-keys))
(sc/def :lytek/attribute coerce-to-keyword)
(s/def :lytek/attributes
  (s/map-of :lytek/attribute (s/int-in 1 6)))

(s/def :lytek/ability
  (set ability-keys))
(sc/def :lytek/ability coerce-to-keyword)
(s/def :lytek/abilities
  (s/and
    (s/map-of :lytek/ability :lytek/rank)
    #(not (or (:craft %) (:martial-arts %)))))

(s/def :lytek/additional-ability
  (s/tuple :lytek/ability
           :lytek/rank
           :lytek/description))
(sc/def :lytek/additional-ability (fn [[a r d]]
                                    [(coerce-to-keyword a)
                                     (sc/coerce :lytek/rank r)
                                     (sc/coerce :lytek/description d)]))
(s/def :lytek/additional-abilities
  (s/coll-of :lytek/additional-ability))


(s/def :lytek/supernal
  :lytek/ability)
(sc/def :lytek/ability coerce-to-keyword)
(s/def :lytek/favored-abilities
  (s/coll-of :lytek/ability :count 10 :into #{}))

(s/def :lytek/specialty
  (s/tuple :lytek/ability
           :lytek/description))
(s/def :lytek/specialties
  (s/coll-of :lytek/specialty))  

(s/def :lytek/charms
  (s/coll-of :lytek/name :into []))

(s/def :lytek/anima
  string?)
(sc/def :lytek/anima coerce-to-string)

(s/def :lytek/health-boxes
  (s/int-in 0 51))
(s/def :lytek/health-levels
  (s/coll-of :lytek/health-boxes :count 4 :into []))
(s/def :lytek/damage-bashing
  :lytek/health-boxes)
(s/def :lytek/damage-lethal
  :lytek/health-boxes)
(s/def :lytek/damage-aggravated
  :lytek/health-boxes)
(s/def :lytek/healthy
  (s/keys :req-un [:lytek/health-levels
                   :lytek/damage-bashing
                   :lytek/damage-lethal
                   :lytek/damage-aggravated]))

(s/def :lytek/willpower-temporary (s/int-in 0 11))
(s/def :lytek/willpower-maximum (s/int-in 0 11))

(s/def :lytek/limit-trigger string?)
(s/def :lytek/limit-accrued (s/int-in 0 11))

(s/def :lytek/essence-rating
  (s/int-in 1 6))

(s/def :lytek/motepool
  (s/int-in 0 200))
(s/def :lytek/essence-personal
  :lytek/motepool)         
(s/def :lytek/essence-peripheral
  :lytek/motepool)
(s/def :lytek/committed-personal
  :lytek/motepool)
(s/def :lytek/committed-peripheral
  :lytek/motepool)

(s/def :lytek/intimacy-type
  #{:principle :tie})
(sc/def :lytek/intimacy-type coerce-to-keyword)
(s/def :lytek/intimacy-severity
  #{:defining :major :minor})
(sc/def :lytek/intimacy-severity coerce-to-keyword)
(s/def :lytek/intimacy-feeling
  string?)
(sc/def :lytek/intimacy-feeling coerce-to-string)
(s/def :lytek/intimacy
  (s/tuple :lytek/intimacy-severity
           :lytek/intimacy-type
           :lytek/intimacy-feeling
           (s/and :lytek/description
                  not-blank?)))
(sc/def :lytek/intimacy (fn [[s t f d]]
                          [(coerce-to-keyword s)
                           (coerce-to-keyword t)
                           (coerce-to-string f)
                           (coerce-to-string d)]))
(s/def :lytek/intimacies
  (s/coll-of :lytek/intimacy :into [] :min-count 4))  


(s/def :lytek/entity
  (s/keys :req-un [:lytek/id
                   :lytek/category
                   :lytek/subcategory
                   :lytek/name
                   :lytek/description]))

(s/def :lytek/combatant
  (s/merge :lytek/entity
           :lytek/healthy
           (s/keys :req-un [:lytek/attributes
                            :lytek/abilities
                            :lytek/additional-abilities
                            :lytek/willpower-maximum
                            :lytek/willpower-temporary
                            :lytek/intimacies])))

(s/def :lytek/character
  (s/and
    (s/merge :lytek/combatant
             (s/keys :req-un [:lytek/subcategory
                              :lytek/anima
                              :lytek/rulebooks
                              :lytek/charms
                              :lytek/owner]
                     :opt-un [:lytek/background
                              :lytek/title]))
    #(= (:category %) :character)))

(s/def :lytek/enlightened
  (s/merge :lytek/character
           (s/keys :req-un [:lytek/essence-rating
                            :lytek/essence-personal
                            :lytek/essence-peripheral
                            :lytek/committed-personal
                            :lytek/committed-peripheral])))

(s/def :lytek/solar
  (s/and
    (s/merge :lytek/enlightened
            (s/keys :req-un [:lytek/limit-trigger
                             :lytek/limit-accrued
                             :lytek/supernal
                             :lytek/favored-abilities]))
    #(contains? solar-castes (:subcategory %))))

(defn get-applicable-spec-pre-coersion [{:as entity :keys [category subcategory]}]
  (cond
    (= category :character) (cond
                              (contains? solar-castes subcategory) :lytek/solar
                              (contains? terrestrial-aspects subcategory) :lytek/terrestrial
                              :else :lytek/character)
    :else :lytek/entity))
