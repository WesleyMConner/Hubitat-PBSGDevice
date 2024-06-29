/*
Boolean revisedHealthyButtonsString(String spaceDelimitedButtons) {
  // This method:
  //   - Logs ANY issues with the provided string and immediately returns FALSE.
  //   - If the provided string meets sufficiency requirements AND represents
  //     a change in the buttons OR their order:
  //       - Normalize settings.buttons (buttons delimited with a single space)
  //       - Update the ArrayList 'atomicState.buttonsList'
  //       - Update the attribute 'numberOfButtons' if changed.
  //       - Return TRUE indicating that a PBSG rebuilt is required.
  Boolean revisedButtons = false
  String markDirty = buttons?.replaceAll(/[\W_&&[^_ ]]/, '▮')
  ArrayList newButtonsList = spaceDelimitedButtons.tokenize(' ')
  Integer newButtonsCount = newButtonsList.size()
  ArrayList oldButtonsList = atomicState.buttonsList
  if (spaceDelimitedButtons == null) {
    logError('revisedHealthyButtonsString', ['',
      "Argument 'spaceDelimitedStrings' is null.",
      'If a PBSG exist, it is left AS IS.'
    ])
  } else if (buttons != markDirty) {
    logError('updatedButtonsString', ['',
      "Invalid ${b('buttons')} string:",
      buttons,
      "${markDirty} ('▮' denotes problematic characters)",
      'If a PBSG exist, it is left AS IS.'
    ])
  } else if (newButtonsCount < 2) {
    logError('revisedHealthyButtonsString', ['',
      'Two buttons are required to proceed',
      "Found ${newButtonsCount} buttons: ${buttonsList.join(', ')}",
      'If a PBSG exist, it is left AS IS.'
    ])
  } else if (! newButtonsList.equals(oldButtonsList)) {
    // Sufficiency requirements are met AND the buttons list has changed.
    atomicState.buttonsList = buttonsList
    device.updateSetting('buttons', [value: buttonsList.join(' '), type: 'String'])
    Integer oldButtonsCount = oldButtonsList.size()
    if (newButtonsCount != newButtonsCount) {
      String desc = "${i(oldButtonsCount)} → ${b(newButtonsCount)}"
      logTrace(
        'updatedButtonsString',
        "Updating attribute ${b('numberOfButtons')}: ${desc}"
      )
      device.sendEvent(
        name: 'numberOfButtons',
        isStateChange: true,
        value: newButtonsCount,
        unit: '#',
        descriptionText: desc
      )
    }
    revisedButtons = true
  }
  return revisedButtons
}

Boolean revisedHealthyDfltString(String dflt) {
  // This method:
  //   - Ensures 'dflt' (if provided) is a legitimate button.
  //   - Preserves the working default button as 'atomicState.dfltButton'.
  //   - When the default is revised, this method returns TRUE (indicating a
  //     PBSG rebuild may be required) otherwise it returns FALSE.
  Boolean revisedDflt
  String newDflt = (dflt == 'not_applicable') ? null : dflt
  ArrayList buttonsList = atomicState.buttonsList
  if (newDflt && (! buttonsList?.contains(newDflt))) {
    logError(
      'revisedHealthyDfltString',
      "The settings.dflt (${b(dflt)}) is not found in buttons (${bList(buttonsList)})"
    )
  } else if (newDflt != atomicState.dfltButton) {
    atomicState.dfltButton = newDflt
    revisedDflt = true
  }
  return revisedDflt
}

Boolean revisedHealthyInstType(String instType) {
  // This method:
  //   - The String 'instType' exists to differentate prospective descendants
  //     of PBSG (as a pseudo-Class). Other than scanning for illadvised
  //     characters, offered Strings are accepted essentially as-is.
  //   - As the 'instType' may matter if future applications, changes are
  //     registered into atomicState and applied to the current PBSG (without
  //     fanfare). There is no attribute publication for instType.
  revisedInstType = false
  String markDirty = instType?.replaceAll(/[\W_&&[^_]]/, '▮')
  String oldInstType = atomicState.instType
  if (instType != markDirty) {
      logError('revisedHealthyInstType', ["Invalid ${b('instType')} preference:",
      instType,
      "${markDirty} ('▮' denotes problematic character).",
      'If a PBSG exist, it is left AS IS.'
    ])
  } else if {instType != oldInstType} {
    atomicState.instType = instType
    revisedInstType = true
  }
  return revisedInstType
}

Boolean revisedHealthyLogLevel(String logLevel) {
  // This method:

  result = false
  ArrayList options = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR']
  if (!options.contains(logLevel)) {
    logError(
      'revisedHealthyLogLevel',
      "logLevel (${b(logLevel)}) not in ${bList(options)}."
    )
  } else {
    device.updateSetting('logLevel', [value: logLevel, type: 'Enum'])
    settings?.logLevel && setLogLevel(settings.logLevel)
    result = true
  }
  return result
}
*/