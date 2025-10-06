// Please see documentation at https://learn.microsoft.com/aspnet/core/client-side/bundling-and-minification
// for details on configuring this project to bundle and minify static web assets.

// Write your JavaScript code.
document.addEventListener("DOMContentLoaded", () => {
 
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

    // -------------------------
    // Topbar Hide on Scroll
    // -------------------------
    const topbar = document.querySelector(".topbar");
    let lastScrollY = window.scrollY;

    if (topbar) {
        window.addEventListener("scroll", () => {
            if (window.scrollY > lastScrollY) {
                // Scrolling DOWN → hide topbar
                topbar.classList.add("hidden");
            } else {
                // Scrolling UP → show topbar
                topbar.classList.remove("hidden");
            }

            // Optional: Shadow effect when scrolling
           

            lastScrollY = window.scrollY;
        });
    }

});

// ----------------------------
//  Toggle Password Visibility
// ----------------------------
document.querySelectorAll('.toggle-password').forEach(button => {
    const input = button.previousElementSibling;
    const eyeOpen = button.querySelector('.eye-open');
    const eyeClosed = button.querySelector('.eye-closed');

    button.addEventListener('click', () => {
        if (input.type === 'password') {
            input.type = 'text';
            eyeOpen.style.display = 'none';
            eyeClosed.style.display = 'inline';
        } else {
            input.type = 'password';
            eyeOpen.style.display = 'inline';
            eyeClosed.style.display = 'none';
        }
    });
});