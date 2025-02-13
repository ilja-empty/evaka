// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.shared.auth

import fi.espoo.evaka.shared.Tracing
import fi.espoo.voltti.auth.getDecodedJwt
import fi.espoo.voltti.logging.MdcKey
import io.opentracing.Tracer
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

private const val ATTR_USER = "evaka.user"

fun HttpServletRequest.getAuthenticatedUser(): AuthenticatedUser? =
    getAttribute(ATTR_USER) as AuthenticatedUser?

fun HttpServletRequest.setAuthenticatedUser(user: AuthenticatedUser) = setAttribute(ATTR_USER, user)

class JwtToAuthenticatedUser(private val tracer: Tracer) : HttpFilter() {
    override fun doFilter(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val user = request.getDecodedJwt()?.toAuthenticatedUser()

        if (user != null) {
            request.setAuthenticatedUser(user)
            tracer.activeSpan()?.setTag(Tracing.enduserIdHash, user.rawIdHash)
            MdcKey.USER_ID.set(user.rawId().toString())
            MdcKey.USER_ID_HASH.set(user.rawIdHash.toString())
        }
        try {
            chain.doFilter(request, response)
        } finally {
            MdcKey.USER_ID_HASH.unset()
            MdcKey.USER_ID.unset()
        }
    }
}
