@file:Suppress("NOTHING_TO_INLINE")

package org.jellyfin.mobile.utils.extensions

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.add
import androidx.fragment.app.replace
import org.jellyfin.mobile.R

inline fun <reified T : Fragment> FragmentManager.addFragment(args: Bundle? = null) {
    beginTransaction().apply {
        add<T>(R.id.fragment_container, args = args)
        addToBackStack(null)
    }.commit()
}

/**
 * Like [addFragment] but with a fade/slide transition (enter, exit, and their
 * pop counterparts) so navigating between native screens animates instead of
 * hard-cutting. Animations live in res/anim/fragment_*.xml.
 */
inline fun <reified T : Fragment> FragmentManager.addFragmentAnimated(args: Bundle? = null) {
    beginTransaction().apply {
        setCustomAnimations(
            R.anim.fragment_enter,
            R.anim.fragment_exit,
            R.anim.fragment_pop_enter,
            R.anim.fragment_pop_exit,
        )
        add<T>(R.id.fragment_container, args = args)
        addToBackStack(null)
    }.commit()
}

inline fun <reified T : Fragment> FragmentManager.replaceFragment(args: Bundle? = null) {
    beginTransaction().replace<T>(R.id.fragment_container, args = args).commit()
}
