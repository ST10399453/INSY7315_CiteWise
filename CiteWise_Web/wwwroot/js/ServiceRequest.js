//// ----------------------------
//// Student Service Request Logic
//// ----------------------------

//let currentStep = 1;
//let extraProgress = 0; // stays 0 until user clicks Next on step 2

//function updateProgress() {
//    let progressPercent = 0;

//    // Step 1: Service selected
//    if ($("input[name='SelectedService']:checked").length > 0) progressPercent += 25;

//    // Step 2: Only adds 25% after "Next" on step 2 is clicked
//    progressPercent += extraProgress;

//    // Step 3: File uploaded
//    const fileInput = $("input[name='Documents']")[0];
//    if (fileInput && fileInput.files.length > 0) progressPercent += 25;

//    // Step 4: Deadline (12.5%) + Urgency (12.5%)
//    if ($("input[name='Deadline']").val()) progressPercent += 12.5;
//    if ($("select[name='Urgency']").val()) progressPercent += 12.5;

//    if (progressPercent > 100) progressPercent = 100;
//    $(".progress-fill").css("width", progressPercent + "%");
//}

//// ----------------------------
//// Step Navigation
//// ----------------------------
//window.nextStep = function (step) {
//    const form = $("form");
//    const validator = form.validate({ ignore: [] });

//    // Clear previous validation messages
//    $("span[data-valmsg-for]").text("");

//    // Step 1 validation
//    if (currentStep === 1 && $("input[name='SelectedService']:checked").length === 0) {
//        validator.showErrors({ SelectedService: "Please select the type of service you need." });
//        $("input[name='SelectedService']").first().focus();
//        return;
//    }

//    // Leaving Step 2 → mark as completed
//    if (currentStep === 2 && step > currentStep) extraProgress = 25;

//    // Going back before Step 2 → reset progress
//    if (step <= 1) extraProgress = 0;

//    // Step 3 validation
//    if (currentStep === 3) {
//        const docNameInput = $("input[name='DocName']").val().trim();
//        const fileInput = $("input[name='Documents']")[0];
//        let hasError = false;

//        if (!docNameInput) {
//            validator.showErrors({ DocName: "Please enter a document name." });
//            $("input[name='DocName']").focus();
//            hasError = true;
//        }

//        if (!fileInput || fileInput.files.length === 0) {
//            validator.showErrors({ Documents: "Please upload a document." });
//            if (!hasError) $("input[name='Documents']").focus();
//            hasError = true;
//        }

//        if (hasError) return;
//    }

//    // Step 4 validation (final submission)
//    if (currentStep === 4) {
//        const deadline = $("input[name='Deadline']").val();
//        const urgency = $("select[name='Urgency']").val();

//        if (!deadline) {
//            validator.showErrors({ Deadline: "Please enter a valid deadline date." });
//            $("input[name='Deadline']").focus();
//            return;
//        }

//        if (!urgency) {
//            validator.showErrors({ Urgency: "Please select an urgency level." });
//            $("select[name='Urgency']").focus();
//            return;
//        }

//        form.submit();
//        return;
//    }

//    // Step transition
//    $(`#step${currentStep}`).hide();
//    $(`#step${step}`).show();
//    currentStep = step;

//    updateProgress();
//};

//// ----------------------------
//// File Upload Display
//// ----------------------------
//const docUpload = document.getElementById("documentUpload");
//if (docUpload) {
//    docUpload.addEventListener("change", (e) => {
//        const fileNameDisplay = document.getElementById("selectedFileName");
//        const fileName = e.target.files[0]?.name;

//        if (fileName) {
//            fileNameDisplay.textContent = fileName;
//            fileNameDisplay.classList.add("show");
//        } else {
//            fileNameDisplay.textContent = "";
//            fileNameDisplay.classList.remove("show");
//        }

//        updateProgress();
//    });
//}

//// ----------------------------
//// Custom Urgency Dropdown
//// ----------------------------
//const trigger = document.getElementById("urgencyTrigger");
//const menu = document.getElementById("urgencyMenu");
//const display = document.getElementById("urgencyDisplay");
//const select = document.getElementById("urgencySelect");

//if (trigger && menu && display && select) {
//    const options = menu.querySelectorAll(".urgency-option");

//    // Toggle dropdown
//    trigger.addEventListener("click", (e) => {
//        e.stopPropagation();
//        trigger.classList.toggle("active");
//        menu.classList.toggle("show");
//    });

//    // Close dropdown when clicking outside
//    document.addEventListener("click", (e) => {
//        if (!trigger.contains(e.target) && !menu.contains(e.target)) {
//            trigger.classList.remove("active");
//            menu.classList.remove("show");
//        }
//    });

//    // Handle option selection
//    options.forEach((option) => {
//        option.addEventListener("click", function () {
//            const value = this.getAttribute("data-value");
//            const indicator = this.querySelector(".urgency-indicator").cloneNode(true);
//            const text = this.querySelector(".urgency-text").cloneNode(true);

//            // Update hidden select (so model binding works)
//            select.value = value;

//            // Update visible display
//            display.innerHTML = "";
//            display.appendChild(indicator);
//            display.appendChild(text);

//            // Highlight selected
//            options.forEach((opt) => opt.classList.remove("selected"));
//            this.classList.add("selected");

//            // Close dropdown
//            trigger.classList.remove("active");
//            menu.classList.remove("show");

//            updateProgress(); // reflect 12.5% progress if needed
//        });
//    });
//}

//// ----------------------------
//// Deadline change triggers progress
//// ----------------------------
//$("input[name='Deadline']").on("change input", updateProgress);
// ----------------------------
// Student Service Request Logic + Deadline Flatpickr
// ----------------------------

let currentStep = 1;
let extraProgress = 0;

function updateProgress() {
    let progressPercent = 0;

    if ($("input[name='SelectedService']:checked").length > 0) progressPercent += 25;
    progressPercent += extraProgress;

    const fileInput = $("input[name='Documents']")[0];
    if (fileInput && fileInput.files.length > 0) progressPercent += 25;

    if ($("input[name='Deadline']").val()) progressPercent += 12.5;
    if ($("select[name='Urgency']").val()) progressPercent += 12.5;

    if (progressPercent > 100) progressPercent = 100;
    $(".progress-fill").css("width", progressPercent + "%");
}

// ----------------------------
// Step Navigation
// ----------------------------
window.nextStep = function(step) {
    const form = $("form");
    const validator = form.validate({ ignore: [] });

    $("span[data-valmsg-for]").text("");

    if (currentStep === 1 && $("input[name='SelectedService']:checked").length === 0) {
        validator.showErrors({ SelectedService: "Please select the type of service you need." });
        $("input[name='SelectedService']").first().focus();
        return;
    }

    if (currentStep === 2 && step > currentStep) extraProgress = 25;
    if (step <= 1) extraProgress = 0;

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

    if (currentStep === 4) {
        const deadline = $("input[name='Deadline']").val()?.trim();
        const urgency = $("select[name='Urgency']").val()?.trim();

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

    $(`#step${currentStep}`).hide();
    $(`#step${step}`).show();
    currentStep = step;

    updateProgress();
};

// ----------------------------
// File Upload Display
// ----------------------------
const docUpload = document.getElementById("documentUpload");
if (docUpload) {
    docUpload.addEventListener("change", (e) => {
        const fileNameDisplay = document.getElementById("selectedFileName");
        const fileName = e.target.files[0]?.name;

        if (fileName) {
            fileNameDisplay.textContent = fileName;
            fileNameDisplay.classList.add("show");
        } else {
            fileNameDisplay.textContent = "";
            fileNameDisplay.classList.remove("show");
        }

        updateProgress();
    });
}

// ----------------------------
// Custom Urgency Dropdown
// ----------------------------
const trigger = document.getElementById("urgencyTrigger");
const menu = document.getElementById("urgencyMenu");
const display = document.getElementById("urgencyDisplay");
const select = document.getElementById("urgencySelect");

if (trigger && menu && display && select) {
    const options = menu.querySelectorAll(".urgency-option");

    trigger.addEventListener("click", (e) => {
        e.stopPropagation();
        trigger.classList.toggle("active");
        menu.classList.toggle("show");
    });

    document.addEventListener("click", (e) => {
        if (!trigger.contains(e.target) && !menu.contains(e.target)) {
            trigger.classList.remove("active");
            menu.classList.remove("show");
        }
    });

    options.forEach((option) => {
        option.addEventListener("click", function () {
            const value = this.getAttribute("data-value");
            const indicator = this.querySelector(".urgency-indicator").cloneNode(true);
            const text = this.querySelector(".urgency-text").cloneNode(true);

            select.value = value;

            display.innerHTML = "";
            display.appendChild(indicator);
            display.appendChild(text);

            options.forEach((opt) => opt.classList.remove("selected"));
            this.classList.add("selected");

            trigger.classList.remove("active");
            menu.classList.remove("show");

            const form = $("form");
            const validator = form.validate();
            if (validator) validator.element(select);

            updateProgress();
        });
    });
}

// ----------------------------
// Deadline triggers progress
// ----------------------------
$("input[name='Deadline']").on("change input", updateProgress);
