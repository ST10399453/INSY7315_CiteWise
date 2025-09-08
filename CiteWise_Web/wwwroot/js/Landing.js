document.addEventListener("DOMContentLoaded", () => {
    const menu = document.getElementById("menu");
    const navLinks = document.getElementById("navLinks");

    menu.addEventListener("click", () => {
        navLinks.classList.toggle("active");

        if (navLinks.classList.contains("active")) {
            menu.textContent = "✖"; // change to X
        } else {
            menu.textContent = "☰"; // change back to hamburger
        }
    });
});
