
document.addEventListener("DOMContentLoaded", () => {
    const menu = document.getElementById("menu");
    const navLinks = document.getElementById("navLinks");
    const closeBtn = document.getElementById("closeBtn");

    menu.addEventListener("click", () => {
        navLinks.classList.add("active");
    });

    closeBtn.addEventListener("click", () => {
        navLinks.classList.remove("active");
    });
});
