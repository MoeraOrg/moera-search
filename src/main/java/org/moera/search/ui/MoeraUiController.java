package org.moera.search.ui;

import org.moera.search.global.UiController;
import org.moera.search.global.VirtualPage;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@UiController
@RequestMapping("/moera")
public class MoeraUiController {

    @RequestMapping(method = {RequestMethod.GET, RequestMethod.HEAD})
    @VirtualPage
    public String index() {
        return "redirect:/";
    }

    @RequestMapping(method = {RequestMethod.GET, RequestMethod.HEAD}, path = "/search", produces = "text/html")
    @VirtualPage
    public String search() {
        return "redirect:/";
    }

}
