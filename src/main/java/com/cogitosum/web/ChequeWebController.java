package com.cogitosum.web;

import com.cogitosum.entity.PaymentMethod;
import com.cogitosum.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/cheques")
public class ChequeWebController {

    @Autowired private PaymentService paymentService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("cheques", paymentService.getPaymentsByMethod(PaymentMethod.CHEQUE));
        return "cheques/list";
    }
}