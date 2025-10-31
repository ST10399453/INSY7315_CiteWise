document.addEventListener("DOMContentLoaded", function () {
    function addWorkingDays(date, daysToAdd) {
        const result = new Date(date.getTime());
        let addedDays = 0;

        while (addedDays < daysToAdd) {
            result.setDate(result.getDate() + 1);
            if (result.getDay() !== 0 && result.getDay() !== 6) addedDays++;
        }

        return result;
    }

    const minWorkingDate = addWorkingDays(new Date(), 5); // 5 working days from today

    const picker = flatpickr("#deadlinePicker", {
        dateFormat: "Y-m-d",
        altInput: true,
        altFormat: "Y / m / d",
        allowInput: true,
        minDate: minWorkingDate,
        disable: [
            function (date) { return date.getDay() === 0 || date.getDay() === 6; }
        ],
        // ✅ Add this to trigger validation + progress updates
        onChange: function (selectedDates, dateStr) {
            const deadlineInput = $("input[name='Deadline']");
            deadlineInput.val(dateStr);

            // Re-validate this field and remove error if valid
            const form = $("form");
            const validator = form.validate();
            if (validator) validator.element(deadlineInput);

            updateProgress(); // update the progress bar
        }
    });

    document.querySelector(".calendar-icon").addEventListener("click", () => picker.open());

    $.validator.setDefaults({ ignore: [] });
    $.validator.unobtrusive.parse("form");
});
