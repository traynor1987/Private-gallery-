package uk.co.traynor.privategallery

import androidx.lifecycle.ViewModelProvider

/** Establish the same in-memory media destination and Home admission as an explicit Vault launch. */
internal fun enterExternalEditingMediaFixture(activity:MainActivity) {
 val state=ViewModelProvider(activity)[ProtectedSessionState::class.java]
 state.homeVisible=false
 state.homePrimaryAuthentication=state.session.captureAuthenticationEpoch()
 if(state.hidden.selected) {
  state.hidden.authenticated=true
  state.homeHiddenAuthentication=state.hidden.session.authentication()
  state.hidden.select(Route.VAULT)
 }
 MainActivity::class.java.getDeclaredMethod("setRoute",Route::class.java).apply{isAccessible=true}.invoke(activity,Route.VAULT)
}
