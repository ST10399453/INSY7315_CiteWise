let currentStep = 1
let extraProgress = 0
const $ = window.jQuery // Declare the $ variable

function updateProgress() {
    let progressPercent = 0

    if ($("input[name='SelectedService']:checked").length > 0) progressPercent += 25
    progressPercent += extraProgress

    const fileInput = $("input[name='Documents']")[0]
    if (fileInput && fileInput.files.length > 0) progressPercent += 25

    if ($("input[name='Deadline']").val()) progressPercent += 12.5
    if ($("select[name='Urgency']").val()) progressPercent += 12.5

    if (progressPercent > 100) progressPercent = 100
    $(".progress-fill").css("width", progressPercent + "%")
}

// ----------------------------
// Step Navigation
// ----------------------------
window.nextStep = (step) => {
    const form = $("form")
    const validator = form.validate({ ignore: [] })

    $("span[data-valmsg-for]").text("")

    if (currentStep === 1 && $("input[name='SelectedService']:checked").length === 0) {
        validator.showErrors({ SelectedService: "Please select the type of service you need." })
        $("input[name='SelectedService']").first().focus()
        return
    }

    if (currentStep === 2 && step > currentStep) extraProgress = 25
    if (step <= 1) extraProgress = 0

    if (currentStep === 3) {
        const docNameInput = $("input[name='DocName']").val().trim()
        const fileInput = $("input[name='Documents']")[0]
        let hasError = false

        if (!docNameInput) {
            validator.showErrors({ DocName: "Please enter a document name." })
            $("input[name='DocName']").focus()
            hasError = true
        }

        if (!fileInput || fileInput.files.length === 0) {
            validator.showErrors({ Documents: "Please upload a document." })
            if (!hasError) $("input[name='Documents']").focus()
            hasError = true
        }

        if (hasError) return
    }

    if (currentStep === 4) {
        const deadline = $("input[name='Deadline']").val()?.trim()
        const urgency = $("select[name='Urgency']").val()?.trim()

        if (!deadline) {
            validator.showErrors({ Deadline: "Please enter a valid deadline date." })
            $("input[name='Deadline']").focus()
            return
        }

        if (!urgency) {
            validator.showErrors({ Urgency: "Please select an urgency level." })
            $("select[name='Urgency']").focus()
            return
        }

        form.submit()
        return
    }

    $(`#step${currentStep}`).hide()
    $(`#step${step}`).show()
    currentStep = step

    updateProgress()
}

// ----------------------------
// File Upload Display
// ----------------------------
const docUpload = document.getElementById("documentUpload")
if (docUpload) {
    docUpload.addEventListener("change", (e) => {
        const fileNameDisplay = document.getElementById("selectedFileName")
        const fileName = e.target.files[0]?.name

        if (fileName) {
            fileNameDisplay.textContent = fileName
            fileNameDisplay.classList.add("show")
        } else {
            fileNameDisplay.textContent = ""
            fileNameDisplay.classList.remove("show")
        }

        updateProgress()
    })

    const observer = new MutationObserver(() => {
        const container = docUpload.closest(".file-upload-container")
        if (container) {
            if (docUpload.classList.contains("input-validation-error")) {
                container.classList.add("input-validation-error")
            } else {
                container.classList.remove("input-validation-error")
            }
        }
    })

    observer.observe(docUpload, {
        attributes: true,
        attributeFilter: ["class"],
    })

    // Check initial state on page load
    const container = docUpload.closest(".file-upload-container")
    if (container && docUpload.classList.contains("input-validation-error")) {
        container.classList.add("input-validation-error")
    }
}

// ----------------------------
// Custom Urgency Dropdown
// ----------------------------
const trigger = document.getElementById("urgencyTrigger")
const menu = document.getElementById("urgencyMenu")
const display = document.getElementById("urgencyDisplay")
const select = document.getElementById("urgencySelect")

if (trigger && menu && display && select) {
    const options = menu.querySelectorAll(".urgency-option")

    trigger.addEventListener("click", (e) => {
        e.stopPropagation()
        trigger.classList.toggle("active")
        menu.classList.toggle("show")
    })

    document.addEventListener("click", (e) => {
        if (!trigger.contains(e.target) && !menu.contains(e.target)) {
            trigger.classList.remove("active")
            menu.classList.remove("show")
        }
    })

    options.forEach((option) => {
        option.addEventListener("click", function () {
            const value = this.getAttribute("data-value")
            const indicator = this.querySelector(".urgency-indicator").cloneNode(true)
            const text = this.querySelector(".urgency-text").cloneNode(true)

            select.value = value

            display.innerHTML = ""
            display.appendChild(indicator)
            display.appendChild(text)

            options.forEach((opt) => opt.classList.remove("selected"))
            this.classList.add("selected")

            trigger.classList.remove("active")
            menu.classList.remove("show")

            const form = $("form")
            const validator = form.validate()
            if (validator) validator.element(select)

            updateProgress()
        })
    })
}

// ----------------------------
// Deadline triggers progress
// ----------------------------
$("input[name='Deadline']").on("change input", updateProgress)
