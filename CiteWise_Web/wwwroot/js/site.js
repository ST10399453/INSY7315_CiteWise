// Please see documentation at https://learn.microsoft.com/aspnet/core/client-side/bundling-and-minification
// for details on configuring this project to bundle and minify static web assets.

// Write your JavaScript code.
document.addEventListener("DOMContentLoaded", () => {
    // -------------------------
    // Login/Register toggle
    // -------------------------
    //const toggle = document.getElementById("filter");
    //const loginPanel = document.getElementById("loginForm");
    //const registerPanel = document.getElementById("registerForm");
    //const stage = document.getElementById("formsStage");

    //if (toggle && loginPanel && registerPanel && stage) {
    //    // set stage height to the active panel's content height (smooth)
    //    function setStageHeight() {
    //        const active = toggle.checked ? registerPanel : loginPanel;
    //        // use the actual content height
    //        const height = active.scrollHeight;
    //        stage.style.height = height + "px";
    //    }

    //    // apply stage class (controls slide direction via CSS)
    //    function updateStage() {
    //        stage.classList.toggle("show-register", toggle.checked);
    //        setStageHeight();
    //    }

    //    // prevent initial CSS animation: set height without transition then enable transitions
    //    stage.style.transition = "none";
    //    updateStage();
    //    // force reflow then enable transitions (avoid jump on load)
    //    requestAnimationFrame(() => {
    //        stage.style.transition = "";
    //    });

    //    // handle the toggle
    //    toggle.addEventListener("change", updateStage);
    //    // handle window resize (recalc heights)
    //    window.addEventListener("resize", setStageHeight);
    //}





    // -------------------------
    // Sidebar collapse/expand toggle
    // -------------------------
    const toggleBtn = document.querySelector(".toggle-btn");
    const body = document.querySelector("body")
    const sidebar = document.querySelector(".sidebar");

    if (toggleBtn && sidebar) {
        toggleBtn.addEventListener("click", () => {
            sidebar.classList.toggle("collapsed");
            body.classList.toggle("sidebar-collapsed");
        });
    }

});
