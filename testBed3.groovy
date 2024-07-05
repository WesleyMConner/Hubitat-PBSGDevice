// ---------------------------------------------------------------------------------
// T E S T B E D 3
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
#include WesMC.lUtils

definition (
  name: 'TestBed3',
  namespace: 'WesMC',
  author: 'Wesley M. Conner',
  description: 'TestBed3',
  singleInstance: true,
  iconUrl: '',
  iconX2Url: ''
)

preferences {
  page(name: 'TestBed3')
}

Map TestBed3() {
  return dynamicPage(
    name: 'TestBed3',
    title: 'TestBed3',
    uninstall: true
  ) {
    app.updateLabel("TestBed3 (${app.id})")
    section{
      // GIVEN A FIXED, ONE-LAYER MAP
      Map w = [active: 'one', lifo: ['two', 'three', 'four']]
      paragraph "${b('Given Map')}: ${w}"

      // ENCODE AS JSON
      String x = toJson(w)
      paragraph "${b('Json Encoding')}: ${x}"

      // RE-HYDRATE THE MAP IN ONE STEPS
      //Map m = fromJson(x)
      Map m = parseJson(x)
      paragraph "${b('Hydration')}: ${m}"
    }
  }
}
