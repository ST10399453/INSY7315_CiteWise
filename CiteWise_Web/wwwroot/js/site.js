// Please see documentation at https://learn.microsoft.com/aspnet/core/client-side/bundling-and-minification
// for details on configuring this project to bundle and minify static web assets.

// Write your JavaScript code.
document.addEventListener("DOMContentLoaded", () => {
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