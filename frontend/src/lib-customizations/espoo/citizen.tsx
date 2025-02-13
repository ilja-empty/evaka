// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import type { CitizenCustomizations } from 'lib-customizations/types'

import { citizenConfig } from './appConfigs'
import EspooLogo from './assets/EspooLogoPrimary.svg'
import featureFlags from './featureFlags'
import mapConfig from './mapConfig'

const customizations: CitizenCustomizations = {
  appConfig: citizenConfig,
  langs: ['fi', 'sv', 'en'],
  translations: {
    fi: {},
    sv: {},
    en: {}
  },
  cityLogo: {
    src: EspooLogo,
    alt: 'Espoo Logo'
  },
  routeLinkRootUrl: 'https://reittiopas.hsl.fi/reitti/',
  mapConfig,
  featureFlags,
  getMaxPreferredUnits() {
    return 3
  }
}

export default customizations
