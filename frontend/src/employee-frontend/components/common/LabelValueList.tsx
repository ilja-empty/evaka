// SPDX-FileCopyrightText: 2017-2021 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import React, { Fragment, ReactNode } from 'react'
import styled from 'styled-components'
import { fontWeights } from 'lib-components/typography'

type Spacing = 'small' | 'large'
type LabelWidth = '25%' | 'fit-content(40%)'

type Content = {
  label?: ReactNode
  value: ReactNode
  valueWidth?: string
  dataQa?: string
  onlyValue?: boolean
}
type Props = {
  spacing: Spacing
  labelWidth?: LabelWidth
  contents: (Content | false)[]
}

const LabelValueList = React.memo(function LabelValueList({
  spacing,
  contents,
  labelWidth = '25%'
}: Props) {
  return (
    <GridContainer
      spacing={spacing}
      labelWidth={labelWidth}
      size={contents.length}
    >
      {contents
        .filter((content): content is Content => !!content)
        .map(({ label, value, valueWidth, dataQa, onlyValue }, index) =>
          onlyValue ? (
            <OnlyValue index={index + 1} width={valueWidth} key={index}>
              {value}
            </OnlyValue>
          ) : (
            <Fragment key={index}>
              <Label index={index + 1}>{label}</Label>
              <Value index={index + 1} width={valueWidth} data-qa={dataQa}>
                {value}
              </Value>
            </Fragment>
          )
        )}
    </GridContainer>
  )
})

const GridContainer = styled.div<{
  spacing: Spacing
  size: number
  labelWidth: LabelWidth
}>`
  display: grid;
  grid-template-columns: ${(p) => p.labelWidth} auto;
  grid-template-rows: repeat(${({ size }) => size}, auto);
  grid-gap: ${({ spacing }) => (spacing === 'small' ? '0.5em' : '1em')} 4em;
  justify-items: start;
  align-items: baseline;
`

const Label = styled.div<{ index: number }>`
  grid-column: 1;
  grid-row: ${({ index }) => index};
  font-weight: ${fontWeights.semibold};
`

const Value = styled.div<{ index: number; width?: string }>`
  grid-column: 2;
  grid-row: ${({ index }) => index};
  ${({ width }) => width && `width: ${width};`};
`

const OnlyValue = styled.div<{ index: number; width?: string }>`
  grid-column: 1 / -1;
  grid-row: ${({ index }) => index};
  ${({ width }) => width && `width: ${width};`};
`

export default LabelValueList
