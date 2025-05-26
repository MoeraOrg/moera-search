package org.moera.search.ui;

import jakarta.servlet.http.HttpServletRequest;

import org.moera.search.global.VirtualPage;
import org.moera.search.util.UriUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexUiController {

    @GetMapping("/")
    @VirtualPage("/search")
    public String index(HttpServletRequest request, Model model) {
        String endpointUri = UriUtil.createBuilderFromRequest(request).path("/moera/").build().toUriString();
        model.addAttribute("endpointUri", endpointUri);

        return "index";
    }

}
