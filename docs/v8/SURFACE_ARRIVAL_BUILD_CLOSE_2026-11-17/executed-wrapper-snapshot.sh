set -e
mkdir -p work/cubic-arrival/first-baseline
test ! -e work/cubic-arrival/first-baseline/build.exit
git rev-parse HEAD > work/cubic-arrival/first-baseline/source.txt
set +e
bash scripts/v8/cubic_arrival_build.sh > work/cubic-arrival/first-baseline/build-wrapper.log 2>&1
case_code=$?
set -e
printf '%s\n' "$case_code" > work/cubic-arrival/first-baseline/build.exit
exit "$case_code"