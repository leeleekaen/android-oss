package com.kickstarter.viewmodels

import android.support.annotation.NonNull
import android.util.Pair
import com.kickstarter.libs.ActivityViewModel
import com.kickstarter.libs.Environment
import com.kickstarter.libs.rx.transformers.Transformers
import com.kickstarter.libs.rx.transformers.Transformers.takePairWhen
import com.kickstarter.libs.rx.transformers.Transformers.takeWhen
import com.kickstarter.libs.utils.ListUtils
import com.kickstarter.libs.utils.UserUtils
import com.kickstarter.models.User
import com.kickstarter.ui.activities.NewsletterActivity
import com.kickstarter.ui.data.Newsletter
import rx.Observable
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject

interface NewsletterViewModel {

    interface Inputs {
        /** Call when the user toggles the subscribe all switch.  */
        fun sendAllNewsletter(checked: Boolean)

        /** Call when the user toggles the alumni switch. */
        fun sendAlumniNewsletter(checked: Boolean)

        /** Call when the user toggles the arts & news switch. */
        fun sendArtsNewsNewsletter(checked: Boolean)

        /** Call when the user toggles the films switch. */
        fun sendFilmsNewsletter(checked: Boolean)

        /** Call when the user toggles the games switch. */
        fun sendGamesNewsletter(checked: Boolean)

        /** Call when the user toggles the Happening newsletter switch.  */
        fun sendHappeningNewsletter(checked: Boolean)

        /** Call when the user toggles the invent switch */
        fun sendInventNewsletter(checked: Boolean)

        /** Call when the user toggles the Kickstarter News & Events newsletter switch.  */
        fun sendPromoNewsletter(checked: Boolean)

        /** Call when the user toggles the reads switch. */
        fun sendReadsNewsletter(checked: Boolean)

        /** Call when the user toggles the Projects We Love newsletter switch.  */
        fun sendWeeklyNewsletter(checked: Boolean)
    }

    interface Outputs {
        /** Emits user containing settings state. */
        fun user(): Observable<User>

        /** Show a dialog to inform the user that their newsletter subscription must be confirmed via email.  */
         fun showOptInPrompt(): Observable<Newsletter>
    }

    interface Errors {
        /** Emits when there is an error updating the user preferences. */
        fun unableToSavePreferenceError(): Observable<String>
    }

    class ViewModel(@NonNull val environment: Environment) : ActivityViewModel<NewsletterActivity>(environment), Inputs, Errors, Outputs {

        private val client = environment.apiClient()
        private val currentUser = environment.currentUser()
        private val newsletterInput = PublishSubject.create<Pair<Boolean, Newsletter>>()
        private val showOptInPrompt = PublishSubject.create<Newsletter>()
        private val userInput = PublishSubject.create<User>()
        private val updateSuccess = PublishSubject.create<Void>()
        private val userOutput = BehaviorSubject.create<User>()

        private val unableToSavePreferenceError = PublishSubject.create<Throwable>()

        val inputs: Inputs = this
        val outputs: Outputs = this
        val errors: Errors = this

        init {

            this.client.fetchCurrentUser()
                    .retry(2)
                    .compose(Transformers.neverError())
                    .compose(bindToLifecycle())
                    .subscribe(this.currentUser::refresh)

            this.currentUser.observable()
                    .take(1)
                    .compose(bindToLifecycle())
                    .subscribe(this.userOutput::onNext)

            this.currentUser.observable()
                    .compose<Pair<User, Pair<Boolean, Newsletter>>>(takePairWhen<User, Pair<Boolean, Newsletter>>(this.newsletterInput))
                    .filter { us -> requiresDoubleOptIn(us.first, us.second.first) }
                    .map { us -> us.second.second }
                    .compose(bindToLifecycle())
                    .subscribe(this.showOptInPrompt)

            this.userInput
                    .concatMap { updateSettings(it) }
                    .compose(bindToLifecycle())
                    .subscribe { success(it) }

            this.userInput
                    .compose(bindToLifecycle())
                    .subscribe(this.userOutput)

            this.userOutput
                    .window(2, 1)
                    .flatMap<List<User>>({ it.toList() })
                    .map<User>({ ListUtils.first(it) })
                    .compose<User>(takeWhen<User, Throwable>(this.unableToSavePreferenceError))
                    .compose(bindToLifecycle())
                    .subscribe(this.userOutput)

            this.newsletterInput
                    .map { bs -> bs.first }
                    .compose(bindToLifecycle())
                    .subscribe(this.koala::trackNewsletterToggle)
        }

        override fun sendAllNewsletter(checked: Boolean) {
            this.userInput.onNext(this.userOutput.value.toBuilder()
                    .alumniNewsletter(checked)
                    .artsNewsNewsletter(checked)
                    .filmsNewsletter(checked)
                    .gamesNewsletter(checked)
                    .happeningNewsletter(checked)
                    .inventNewsletter(checked)
                    .promoNewsletter(checked)
                    .readsNewsletter(checked)
                    .weeklyNewsletter(checked)
                    .build())
            this.newsletterInput.onNext(Pair(checked, Newsletter.ALL))
        }

        override fun sendAlumniNewsletter(checked: Boolean) {
            this.userInput.onNext(this.userOutput.value.toBuilder().alumniNewsletter(checked).build())
            this.newsletterInput.onNext(Pair(checked, Newsletter.ALUMNI))
        }

        override fun sendArtsNewsNewsletter(checked: Boolean) {
            this.userInput.onNext(this.userOutput.value.toBuilder().artsNewsNewsletter(checked).build())
            this.newsletterInput.onNext(Pair(checked, Newsletter.ARTS))
        }

        override fun sendFilmsNewsletter(checked: Boolean) {
            this.userInput.onNext(this.userOutput.value.toBuilder().filmsNewsletter(checked).build())
            this.newsletterInput.onNext(Pair(checked, Newsletter.FILMS))
        }

        override fun sendGamesNewsletter(checked: Boolean) {
            this.userInput.onNext(this.userOutput.value.toBuilder().gamesNewsletter(checked).build())
            this.newsletterInput.onNext(Pair(checked, Newsletter.GAMES))
        }

        override fun sendHappeningNewsletter(checked: Boolean) {
            this.userInput.onNext(this.userOutput.value.toBuilder().happeningNewsletter(checked).build())
            this.newsletterInput.onNext(Pair(checked, Newsletter.HAPPENING))
        }

        override fun sendInventNewsletter(checked: Boolean) {
            this.userInput.onNext(this.userOutput.value.toBuilder().inventNewsletter(checked).build())
            this.newsletterInput.onNext(Pair(checked, Newsletter.INVENT))
        }

        override fun sendPromoNewsletter(checked: Boolean) {
            this.userInput.onNext(this.userOutput.value.toBuilder().promoNewsletter(checked).build())
            this.newsletterInput.onNext(Pair(checked, Newsletter.PROMO))
        }

        override fun sendReadsNewsletter(checked: Boolean) {
            this.userInput.onNext(this.userOutput.value.toBuilder().readsNewsletter(checked).build())
            this.newsletterInput.onNext(Pair(checked, Newsletter.READS))
        }

        override fun sendWeeklyNewsletter(checked: Boolean) {
            this.userInput.onNext(this.userOutput.value.toBuilder().weeklyNewsletter(checked).build())
            this.newsletterInput.onNext(Pair(checked, Newsletter.WEEKLY))
        }

        override fun showOptInPrompt(): Observable<Newsletter>  = this.showOptInPrompt

        override fun user() = this.userOutput

        override fun unableToSavePreferenceError() : Observable<String> {
           return this.unableToSavePreferenceError
                    .takeUntil(this.updateSuccess)
                    .map {_ -> null }
        }

        private fun requiresDoubleOptIn(user: User, checked: Boolean) = UserUtils.isLocationGermany(user) && checked

        private fun success(user: User) {
            this.currentUser.refresh(user)
            this.updateSuccess.onNext(null)
        }

        private fun updateSettings(user: User): Observable<User> {
            return this.client.updateUserSettings(user)
                    .compose(Transformers.pipeErrorsTo<User>(this.unableToSavePreferenceError))
        }
    }
}