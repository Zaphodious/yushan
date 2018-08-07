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
            [spec-coerce.core :as sc]
            [clojure.spec.alpha :as s]))

(defn named? [a] (or (string? a) (keyword? a) (symbol? a)))
(defn coerce-to-string [a]
  (if (named? a)
    (name a) (str a)))
(defn coerce-to-keyword [a]
  (keyword (coerce-to-string a)))
(defn coerce-to-int [a]
  (try
    (Integer/parseInt (coerce-to-string a))
    (catch Exception e
      0)))
(def ways-of-depicting-true
  (let [true-strings ["true" "1" "yes" "t" "y"]
        true-keys (map coerce-to-keyword true-strings)
        true-numerically [1]]
      (-> true-strings (into true-keys) (into true-numerically))))
(defn coerce-to-boolean [a]
  (if (first (filter #(= % a) ways-of-depicting-true))
      true false))

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

(sc/def :lytek/img
  string?)

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
    :rulebook
    :castable})
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

(def castable-types
  #{:charm
    :evocation
    :spell
    :martial-arts})

(s/def :lytek/subcategory
  (se/union
    castable-types
    solar-castes
    terrestrial-aspects))
(sc/def :lytek/subcategory coerce-to-keyword)

(s/def :lytek/rank  
  (s/int-in 0 6))
(sc/def :lytek/rank
  coerce-to-int)

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

(s/def :lytek.character/charms
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

(s/def :lytek/merit
  (s/tuple :lytek/name :lytek/rank :lytek/description))

(s/def :lytek/merits
  (s/coll-of :lytek/merit :into []))

(s/def :lytek/charm-type
  #{:supplemental
    :reflexive
    :simple
    :permanent})
(sc/def :lytek/charm-type
  coerce-to-keyword)

(s/def :lytek/duration
  string?)
(sc/def :lytek/duration
  coerce-to-string)

(s/def :lytek/cost
  string?)
(sc/def :lytek/cost
  coerce-to-string)

(s/def :lytek.rulebook.charm/keyword
  #{:aggravated
    :bridge
    :clash
    :counterattack
    :decisive-only
    :dual
    :mute
    :pilot
    :psyche
    :perilous
    :salient
    :stackable
    :uniform
    :withering-only
    :written-only})
(sc/def :lytek.rulebook.charm/keyword
  coerce-to-keyword)

(s/def :lytek/charm-keywords
  (s/coll-of :lytek.rulebook.charm/keyword :into #{}))

(s/def :lytek/prerequisites
  (s/coll-of :lytek/name :into #{}))
(sc/def :lytek/prerequisites
  (fn [a] (into #{} (map coerce-to-string a))))

(s/def :lytek/from-artifact
  :lytek/name)
(sc/def :lytek/from-artifact
  coerce-to-string)

(s/def :lytek/page
  pos-int?)
(sc/def :lytek/page
  coerce-to-int)

(s/def :lytek/rulebook-charm
  (s/keys :req-un [:lytek/name
                   :lytek/description
                   :lytek/ability
                   :lytek/rank
                   :lytek/essence-rating
                   :lytek/page
                   :lytek/charm-type
                   :lytek/duration
                   :lytek/cost
                   :lytek/prerequisites
                   :lytek/charm-keywords]
          :opt-un [:lytek/from-artifact]))

(def sorcery-circles
  #{:terrestrial
    :celestial
    :solar
    :shadowlands
    :labyrinth
    :void})
(s/def :lytek/sorcery-circle
  sorcery-circles)


(s/def :lytek/castable
  (s/and
    (s/merge :lytek/entity
             (s/keys :req-un [:lytek/cost
                              :lytek/duration
                              :lytek/page
                              :lytek/prerequisites
                              :lytek/charm-keywords]
                     :opt-un [:lytek/ability
                              :lytek/from-artifact
                              :lytek/sorcery-circle]))
    #(= (:category %) :castable)
    #(contains? castable-types (:subcategory %))))

(s/def :lytek/rulebook-charms
  (s/coll-of :lytek/rulebook-charm))

(s/def :lytek/repurchasable
  boolean?)
(sc/def :lytek/repurchasable
  coerce-to-boolean)

(s/def :lytek/merit-type
  #{:innate
    :purchased
    :story})
(sc/def :lytek/merit-type
  coerce-to-keyword)

(s/def :lytek/available-ranks
  (s/coll-of (s/int-in 0 6) :into #{} :max-count 6 :min-count 1))
(sc/def :lytek/available-ranks
  (fn [a] (into #{} (map coerce-to-int a))))

(s/def :lytek/grants-merits
  (s/coll-of :lytek/name :into []))

(s/def :lytek/rulebook-merit
  (s/keys :req-un [:lytek/name
                   :lytek/description
                   :lytek/page
                   :lytek/grants-merits
                   :lytek/repurchasable
                   :lytek/merit-type
                   :lytek/available-ranks]))

(s/def :lytek/rulebook-merits
  (s/coll-of :lytek/rulebook-merit :into []))

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
                              :lytek.character/charms
                              :lytek/owner
                              :lytek/merits]
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

(s/def :lytek/rulebook
  (s/and
    (s/merge :lytek/entity
             (s/keys :req-un [:lytek/rulebook-charms
                              :lytek/rulebook-merits]))
    #(= (:category %) :rulebook)))

(defn get-applicable-spec-pre-coersion [{:as entity :keys [category subcategory]}]
  (cond
    (= category :character) (cond
                              (contains? solar-castes subcategory) :lytek/solar
                              (contains? terrestrial-aspects subcategory) :lytek/terrestrial
                              :else :lytek/character)
    (= category :rulebook) :lytek/rulebook
    :else :lytek/entity))
