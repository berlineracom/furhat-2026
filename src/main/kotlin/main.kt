package furhatos.app.newskill1

import furhatos.app.newskill1.flow.Init
import furhatos.flow.kotlin.Flow
import furhatos.skills.Skill

class Newskill1Skill : Skill() {
    override fun start() {
        Flow().run(Init)
    }
}

fun main(args: Array<String>) {
    Skill.main(args)
}
