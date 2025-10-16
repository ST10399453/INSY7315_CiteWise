let currentStep = 1;
let extraProgress = 0; // tracks optional step 2 progress

function updateProgress() {
    let progressPercent = 0;

    // Step 1: Service selected (required)
    if ($("input[name='SelectedService']:checked").length > 0) progressPercent += 25;

    // Step 2: Additional info (optional) → only add if user clicked Next
    progressPercent += extraProgress;

    // Step 3: Document uploaded (required)
    const fileInput = $("input[name='Documents']")[0];
    if (fileInput && fileInput.files.length > 0) progressPercent += 25;

    // Step 4: Deadline & Urgency individually
    if ($("input[name='Deadline']").val()) progressPercent += 12.5;
    if ($("select[name='Urgency']").val()) progressPercent += 12.5;

    if (progressPercent > 100) progressPercent = 100;

    $(".progress-fill").css("width", progressPercent + "%");
}

function nextStep(step) {
    const form = $("form");
    const validator = form.validate({ ignore: [] });

    // Clear previous messages
    $("span[data-valmsg-for]").text("");

    // Step 1: Service required
    if (currentStep === 1 && $("input[name='SelectedService']:checked").length === 0) {
        validator.showErrors({ SelectedService: "Please select the type of service you need." });
        $("input[name='SelectedService']").first().focus();
        return;
    }

    // Step 3: Document required
    if (currentStep === 3) {
        const fileInput = $("input[name='Documents']")[0];
        if (!fileInput || fileInput.files.length === 0) {
            validator.showErrors({ Documents: "Please upload a document." });
            $("input[name='Documents']").focus();
            return;
        }
    }

    // Step 4: Deadline & Urgency required
    if (currentStep === 4) {
        const deadline = $("input[name='Deadline']").val();
        const urgency = $("select[name='Urgency']").val();

        if (!deadline) {
            validator.showErrors({ Deadline: "Please enter a valid deadline date." });
            $("input[name='Deadline']").focus();
            return;
        }

        if (!urgency) {
            validator.showErrors({ Urgency: "Please select an urgency level." });
            $("select[name='Urgency']").focus();
            return;
        }

        form.submit();
        return;
    }

    // If user is leaving Step 2, add extra progress
    if (currentStep === 2) {
        extraProgress = 25; // Step 2 clicked Next → add 10%
    }

    // Hide current / show next
    $("#step" + currentStep).hide();
    $("#step" + step).show();

    currentStep = step;

    // Update progress
    updateProgress();
}

// Optional: live update for Step 4 fields
$("input[name='Deadline'], select[name='Urgency']").on("change input", function () {
    updateProgress();
});
