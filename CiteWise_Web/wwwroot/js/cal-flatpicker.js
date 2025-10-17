document.addEventListener("DOMContentLoaded", function () {
    function addWorkingDays(date, daysToAdd) {
        const result = new Date(date.getTime());
        let addedDays = 0;

        while (addedDays < daysToAdd) {
            result.setDate(result.getDate() + 1);
            // Skip weekends
            if (result.getDay() !== 0 && result.getDay() !== 6) {
                addedDays++;
            }
        }

        return result;
    }

    const minWorkingDate = addWorkingDays(new Date(), 5); // 5 working days from today

    const picker = flatpickr("#deadlinePicker", {
        dateFormat: "Y-m-d",
        altInput: true,
        altFormat: "Y / m / d",
        allowInput: true,
        minDate: minWorkingDate,  // sets minimum date correctly
        disable: [
            function (date) {
                // Disable weekends
                return date.getDay() === 0 || date.getDay() === 6;
            }
        ]
    });

    // Make SVG icon open the picker
    document.querySelector(".calendar-icon").addEventListener("click", () => {
        picker.open();
    });
});