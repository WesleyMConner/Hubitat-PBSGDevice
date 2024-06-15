// ---------------------------------------------------------------------------------
// T E S T B E D 2
//
// Copyright (C) 2023-Present Wesley M. Conner
//
// LICENSE
// Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
// "License"), see http://www.apache.org/licenses/LICENSE-2.0. You may
// not use this file except in compliance with the License. Unless
// required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied.
// ---------------------------------------------------------------------------------
// For reference:
//   Unicode 2190 ← LEFTWARDS ARROW
//   Unicode 2192 → RIGHTWARDS ARROW
import com.hubitat.hub.domain.Event as Event
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

// The Groovy Linter generates NglParseError on Hubitat #include !!!
#include wesmc.lUtils

definition (
  name: 'TestBed2',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'TestBed1',
  singleInstance: true,
  iconUrl: '',
  iconX2Url: ''
)

preferences {
  page(name: 'TestBed2')
}

Map TestBed2() {
  return dynamicPage(
    name: 'TestBed2',
    title: 'TestBed2',
    uninstall: true
  ) {
    app.updateLabel("TestBed2 (${app.id})")
    section{
      // GIVEN A FIXED, ONE-LAYER MAP
      Map w = [active: 'one', lifo: ['two', 'three', 'four']]
      paragraph "${b('Given Map')}: ${w}"

      // CUSTOM ENCODE THE MAP AS A STRING
      String x = "active:${w.active}^lifo:${w.lifo}"
      paragraph "${b('Brute Force Encode It')}: ${x}"

      // RE-HYDRATE THE MAP IN ONE STEPS
      Map m = x.tokenize('^').collectEntries { e -> e.tokenize(':') }
      paragraph "${b('Hydration')}: ${m}"
    }
  }
}
