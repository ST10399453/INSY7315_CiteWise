//let currentStep = 1;

//function nextStep(step) {
//    const form = $("form");
//    let valid = true;

//    // Validate only required steps
//    if (currentStep === 1 || currentStep === 3) {
//        valid = form.valid();
//        if (!valid) {
//            form.validate().focusInvalid();
//            return; // stop if validation fails
//        }
//    }

//    // Navigate steps
//    const current = document.getElementById('step' + currentStep);
//    const next = document.getElementById('step' + step);

//    if (current) current.style.display = 'none';
//    if (next) next.style.display = 'block';

//    currentStep = step;

//    // Update progress bar for all steps
//    const progressFill = document.querySelector(".progress-fill");
//    if (progressFill) {
//        let progressPercent = 0;
//        if (currentStep >= 2) progressPercent = 25;  // Step 1 completed
//        if (currentStep >= 3) progressPercent = 50;  // Step 2 done (optional)
//        if (currentStep >= 4) progressPercent = 75;  // Step 3 completed
//        progressFill.style.width = progressPercent + "%";
//    }
//}
let currentStep = 1;

function nextStep(step) {
    const form = $("form");
    let valid = true;

    // ✅ Validate when leaving Step 1 (Service selection required)
    if (currentStep === 1) {
        const serviceSelected = $("input[name='SelectedService']:checked").length > 0;
        if (!serviceSelected) {
            alert("Please select a service");
            return;
        }
    }

    // ✅ Skip validation for Step 2 (Additional Info is optional)
    // No validation needed when leaving step 2

    // ✅ Validate when leaving Step 3 (Document upload)
    if (currentStep === 3) {
        const fileInput = $("input[name='Documents']");
        if (fileInput[0].files.length === 0) {
            alert("Please upload a document");
            return;
        }
    }

    // ✅ Step navigation
    const current = document.getElementById("step" + currentStep);
    const next = document.getElementById("step" + step);
    if (current) current.style.display = "none";
    if (next) next.style.display = "block";
    currentStep = step;

    // ✅ Progress bar update
    const progressFill = document.querySelector(".progress-fill");
    if (progressFill) {
        let progressPercent = (step / 4) * 100;
        progressFill.style.width = progressPercent + "%";
    }
}