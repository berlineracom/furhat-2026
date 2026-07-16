package furhatos.app.newskill1.flow

import furhatos.app.newskill1.flow.main.Idle
import furhatos.app.newskill1.flow.main.Greeting
import furhatos.app.newskill1.*
import furhatos.app.newskill1.setting.DISTANCE_TO_ENGAGE
import furhatos.app.newskill1.setting.MAX_NUMBER_OF_USERS
import furhatos.flow.kotlin.State
import furhatos.flow.kotlin.furhat
import furhatos.flow.kotlin.state
import furhatos.flow.kotlin.users

val Init: State = state {
    init {
        /** Set our default interaction parameters */
        users.setSimpleEngagementPolicy(DISTANCE_TO_ENGAGE, MAX_NUMBER_OF_USERS)
    }
    onEntry {
        /** start interaction */
        when {
            furhat.isVirtual() -> goto(state = MultiScenarioInit)
            users.hasAny() -> {
                furhat.attend(users.random)
                goto(state = MultiScenarioInit)
            }
            else -> goto(Idle)
        }
    }

}
