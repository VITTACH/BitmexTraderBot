package com.trade.controllers

import com.trade.models.PrefModel
import com.trade.models.UserModel
import com.trade.services.BitmexClothoBot
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.servlet.ModelAndView
import javax.annotation.PostConstruct


@Controller
class BaseController {
    companion object {
        private val userName = "zharikov.vitalik@yandex.ru"
        private val password = "qwerty"
    }

    var prefModel: PrefModel = PrefModel()

    @RequestMapping(value = ["/stopClotho"], method = [RequestMethod.POST])
    fun stopClotho(): ModelAndView {
        BitmexClothoBot.close()
        return ModelAndView("prefs", "prefModel", prefModel)
    }

    @RequestMapping(value = ["/checkLogin"], method = [RequestMethod.POST])
    fun checkLogin(@ModelAttribute("userModel") userModel: UserModel): ModelAndView {
        return if (userModel.userName == userName && userModel.password == password)
            ModelAndView("prefs", "prefModel", prefModel)
        else main()
    }

    @RequestMapping(value = ["/updatePref"], method = [RequestMethod.POST])
    fun updatePref(@ModelAttribute("prefModel") prefModel: PrefModel): ModelAndView {
        this.prefModel = prefModel
        startClotho()
        return ModelAndView("prefs", "prefModel", prefModel)
    }

    @RequestMapping("/")
    fun main(): ModelAndView {
        val userModel = UserModel()
        return ModelAndView("login", "userModel", userModel)
    }

    @PostConstruct
    fun startClotho() {
        BitmexClothoBot.close()
        BitmexClothoBot.start(prefModel)
    }
}