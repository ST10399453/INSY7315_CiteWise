document.addEventListener("DOMContentLoaded", () => {

    // Toggle Password Visibility
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

    // Tabs Active Background & Filtering
    const tabs = document.querySelectorAll('.tab');
    const activeBg = document.querySelector('.active-bg');
    const requests = Array.from(document.querySelectorAll('.request-item'));

    if (tabs.length && activeBg) {
        // Set initial active tab background
        const activeIndex = Array.from(tabs).findIndex(tab => tab.classList.contains('active'));
        if (activeIndex >= 0) {
            activeBg.style.left = `calc(${activeIndex * 25}% + 4px)`;
        }

        tabs.forEach((tab, index) => {
            tab.addEventListener('click', (e) => {
                e.preventDefault(); // prevent navigation

                // Update active class
                document.querySelector('.tab.active')?.classList.remove('active');
                tab.classList.add('active');

                // Move active-bg
                activeBg.style.left = `calc(${index * 25}% + 4px)`;

                // Filter requests
                const selectedPriority = tab.dataset.priority.trim();
                requests.forEach(item => {
                    const badge = item.querySelector('.priority-badge');
                    if (!badge) return;

                    const itemPriority = badge.textContent.trim();
                    if (selectedPriority === "All" || itemPriority.toLowerCase() === selectedPriority.toLowerCase()) {
                        item.style.display = "block";
                    } else {
                        item.style.display = "none";
                    }
                });
            });
        });
    }

});
