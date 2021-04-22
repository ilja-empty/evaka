// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import React from 'react'
import styled from 'styled-components'
import classNames from 'classnames'
import { IconDefinition } from '@fortawesome/fontawesome-svg-core'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import colors, { greyscale } from '../../colors'
import { BaseProps } from '../../utils'
import { IconSize } from '../RoundIcon'

interface ButtonProps {
  size: IconSize | undefined
  gray?: boolean
  white?: boolean
}

const StyledButton = styled.button<ButtonProps>`
  display: flex;
  justify-content: center;
  align-items: center;
  box-sizing: border-box;
  width: calc(
    12px +
      ${(props: ButtonProps) => {
        switch (props.size) {
          case 's':
            return '20px'
          case 'm':
            return '24px'
          case 'L':
            return '34px'
          case 'XL':
            return '64px'
          default:
            return '20px'
        }
      }}
  );
  height: calc(
    12px +
      ${(props: ButtonProps) => {
        switch (props.size) {
          case 's':
            return '20px'
          case 'm':
            return '24px'
          case 'L':
            return '34px'
          case 'XL':
            return '64px'
          default:
            return '20px'
        }
      }}
  );
  font-size: ${(props: ButtonProps) => {
    switch (props.size) {
      case 's':
        return '20px'
      case 'm':
        return '24px'
      case 'L':
        return '34px'
      case 'XL':
        return '64px'
      default:
        return '20px'
    }
  }};
  color: ${(props) =>
    props.gray
      ? colors.greyscale.dark
      : props.white
      ? colors.greyscale.white
      : colors.primary};
  border: none;
  border-radius: 100%;
  background: none;
  outline: none;
  cursor: pointer;
  padding: 0;
  margin: -6px;

  &:focus {
    border: 2px solid ${colors.accents.petrol};
  }

  .icon-wrapper {
    display: flex;
    justify-content: center;
    align-items: center;
    margin: 2px;
  }

  &:hover .icon-wrapper {
    color: ${(props) =>
      props.gray ? colors.greyscale.dark : colors.primaryHover};
  }

  &:active .icon-wrapper {
    color: ${(props) =>
      props.gray ? colors.greyscale.darkest : colors.primaryActive};
  }

  &.disabled {
    cursor: not-allowed;
    .icon-wrapper {
      color: ${greyscale.medium};
    }
  }
`

interface IconButtonProps extends BaseProps {
  onClick?: (e: React.MouseEvent<HTMLButtonElement>) => void
  icon: IconDefinition
  altText?: string
  disabled?: boolean
  size?: IconSize
  gray?: boolean
  white?: boolean
  'data-qa'?: string
}

function IconButton({
  className,
  'data-qa': dataQa,
  icon,
  altText,
  onClick,
  disabled,
  size,
  gray,
  white
}: IconButtonProps) {
  return (
    <StyledButton
      className={classNames(className, { disabled })}
      data-qa={dataQa}
      onClick={onClick}
      disabled={disabled}
      aria-label={altText}
      size={size}
      gray={gray}
      white={white}
    >
      <div className="icon-wrapper">
        <FontAwesomeIcon icon={icon} />
      </div>
    </StyledButton>
  )
}

export default IconButton
