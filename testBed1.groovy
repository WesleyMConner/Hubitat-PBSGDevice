// ---------------------------------------------------------------------------------
// T E S T B E D 1
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

// The Groovy Linter generates NglParseError on Hubitat #include !!!
#include WesMC.lUtils
import com.hubitat.app.ChildDeviceWrapper as ChildDevW
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.app.InstalledAppWrapper as InstAppW
import com.hubitat.hub.domain.Event as Event
import groovy.json.JsonOutput as JsonOutput
import groovy.json.JsonSlurper as JsonSlurper
import java.lang.Math as Math
import java.lang.Object as Object

definition (
  name: 'TestBed1',
  namespace: 'WesMC',
  author: 'Wesley M. Conner',
  description: 'TestBed1',
  singleInstance: true,
  iconUrl: '',
  iconX2Url: ''
)

preferences {
  page(name: 'TestBed1')
}

Map TestBed1() {
  return dynamicPage(
    name: 'TestBed1',
    title: 'TestBed1',
    uninstall: true
  ) {
    app.updateLabel("TestBed1 (${app.id})")
    section{
      // Begin with an empty list.
      ArrayList x = []
      x.push('alpha')
      x.push('beta')
      x.push('gamma')
      x.push('delta')
      paragraph "At 0: ${x.removeAt(0)}"
      paragraph "x: ${x}"
      String s = "This is a test."
      String sJson = toJson(s)
      String s2 = fromJson(sJson)
      paragraph "s: ${s}"
      paragraph "sJson: ${sJson}"
      paragraph "s2: ${s2}"
    }
    section {
      /*
      Map a = [a: 'apple', b: 'banana', c: 'cantelope']
      paragraph "a: ${a}, class: ${a.class}"
      MapOp1(a)
      paragraph "a: ${a}"
      MapOp2(a)
      paragraph "a: ${a}"
      a.x = a.y = 'SOMETHING'
      paragraph "a: ${a}"
      */
      Map a = [a: 'apple', b: 'banana', c: 'cantelope']
      paragraph "a: ${a}"
      paragraph "-----"
      // Use findAll() to create a copy of Map a.
      Map aDup = a.findAll { k, v -> (k) }
      paragraph "aDup: ${aDup}"
      paragraph "-----"
      Map b = [a: 'apricot', d: 'dragon fruit']
      paragraph "b: ${b}"
      paragraph "-----"
      aDup << b
      paragraph "a: ${a}"
      paragraph "aDup w/ b: ${aDup}"
      Map c = [first: 'ITEM', *:aDup]
      paragraph "a: ${a}"
      paragraph "b: ${b}"
      paragraph "aDup w/ b: ${aDup}"
      paragraph "c: ${c}"
    }
    section {
    }
  }
}

void MapOp1(Map m) {
  m << ['d': 'dragon fruit']
}

void MapOp2(Map m) {
  paragraph "MapOp2#1: ${m} (${m.size()})"
  // Use findAll() to create a copy of Map m, then use the copy's keys to
  // remove Map m keys - avoiding the concurrency trap of a Map removing its
  // own keys in a closure construct.
  m.findAll { k, v -> (k) } each { m.remove it.key }
  paragraph "MapOp2#2: ${m}"
  // Insert new keys
  m << [f: 'fig', g: 'grape']
  paragraph "MapOp2#3: ${m}"
}
