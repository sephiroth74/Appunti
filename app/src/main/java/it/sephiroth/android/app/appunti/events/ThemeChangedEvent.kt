package it.sephiroth.android.app.appunti.events

import it.sephiroth.android.app.appunti.models.SettingsManager


/**
 * Appunti
 *
 * @author Alessandro Crugnola on 2019-07-29 - 10:40
 */

open class ThemeChangedEvent(val value: SettingsManager.Companion.DarkThemeBehavior) : RxEvent()