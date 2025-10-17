let currentStep = 1;
let extraProgress = 0; // tracks optional step 2 progress

function updateProgress() {
    let progressPercent = 0;

    // Step 1: Service selected (required)
    if ($("input[name='SelectedService']:checked").length > 0) progressPercent += 25;

    // Step 2: Additional info (optional)
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

    // Clear previous validation messages
    $("span[data-valmsg-for]").text("");

    // --- Step 1: Service required ---
    if (currentStep === 1 && $("input[name='SelectedService']:checked").length === 0) {
        validator.showErrors({ SelectedService: "Please select the type of service you need." });
        $("input[name='SelectedService']").first().focus();
        return;
    }

    // --- Step 3: Document name + file required ---
    if (currentStep === 3) {
        const docNameInput = $("input[name='DocName']").val().trim();
        const fileInput = $("input[name='Documents']")[0];
        let hasError = false;

        if (!docNameInput) {
            validator.showErrors({ DocName: "Please enter a document name." });
            $("input[name='DocName']").focus();
            hasError = true;
        }

        if (!fileInput || fileInput.files.length === 0) {
            validator.showErrors({ Documents: "Please upload a document." });
            if (!hasError) $("input[name='Documents']").focus();
            hasError = true;
        }

        if (hasError) return;
    }

    // --- Step 4: Deadline & Urgency required ---
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

    // --- Step 2 completed (optional progress) ---
    if (currentStep === 2) {
        extraProgress = 25;
    }

    // --- Step transition ---
    $("#step" + currentStep).hide();
    $("#step" + step).show();
    currentStep = step;

    updateProgress();
}

// --- Live update progress for Step 4 ---
$("input[name='Deadline'], select[name='Urgency']").on("change input", function () {
    updateProgress();
});

// --- Show selected file name ---
document.getElementById('documentUpload').addEventListener('change', function (e) {
    const fileNameDisplay = document.getElementById('selectedFileName');
    const fileName = e.target.files[0]?.name;

    if (fileName) {
        fileNameDisplay.textContent = fileName;
        fileNameDisplay.classList.add('show');
    } else {
        fileNameDisplay.classList.remove('show');
    }
});
