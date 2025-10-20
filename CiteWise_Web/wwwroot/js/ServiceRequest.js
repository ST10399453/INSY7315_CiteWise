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

    // --- Step 2: Project title required ---
    if (currentStep === 2) {
        const projectTitle = $("input[name='ProjectTitle']").val().trim();
        if (!projectTitle) {
            validator.showErrors({ ProjectTitle: "Please enter your project name." });
            $("input[name='ProjectTitle']").focus();
            return; // stop if invalid
        }
        extraProgress = 25; // optional textarea can add extra if desired
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

    //// --- Step 2 completed (optional progress) ---
    //if (currentStep === 2) {
    //    extraProgress = 25;
    //}

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
        fileNameDisplay.textContent = "";
        fileNameDisplay.classList.remove('show');
    }
});


// Custom Dropdown JavaScript - Add this to your existing JavaScript file or in a <script> tag

document.addEventListener("DOMContentLoaded", () => {
    const dropdown = document.getElementById("urgencyDropdown")
    const selected = dropdown.querySelector(".urgency-selected")
    const selectedText = dropdown.querySelector(".selected-text")
    const options = dropdown.querySelectorAll(".urgency-option")
    const hiddenSelect = document.getElementById("urgencySelect")

    // Toggle dropdown
    selected.addEventListener("click", (e) => {
        e.stopPropagation()
        dropdown.classList.toggle("open")
    })

    // Handle option selection
    options.forEach((option) => {
        option.addEventListener("click", function (e) {
            e.stopPropagation()

            const value = this.getAttribute("data-value")
            const text = this.querySelector(".option-text").textContent

            // Update visual display
            selectedText.textContent = text

            // Update hidden select value for form submission
            hiddenSelect.value = value

            // Trigger change event for ASP.NET validation
            const event = new Event("change", { bubbles: true })
            hiddenSelect.dispatchEvent(event)

            // Update selected state
            options.forEach((opt) => opt.classList.remove("selected"))
            this.classList.add("selected")

            // Close dropdown
            dropdown.classList.remove("open")
        })
    })

    // Close dropdown when clicking outside
    document.addEventListener("click", (e) => {
        if (!dropdown.contains(e.target)) {
            dropdown.classList.remove("open")
        }
    })
})
