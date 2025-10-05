let currentStep = 1;

function nextStep(step) {
    const current = document.getElementById('step' + currentStep);
    const next = document.getElementById('step' + step);

    if (current) current.style.display = 'none';
    if (next) next.style.display = 'block';

    currentStep = step;
}

function prevStep(step) {
    const current = document.getElementById('step' + currentStep);
    const prev = document.getElementById('step' + step);

    if (current) current.style.display = 'none';
    if (prev) prev.style.display = 'block';

    currentStep = step;
}
