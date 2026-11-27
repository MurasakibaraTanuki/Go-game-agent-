$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
if (-not (Test-Path .git)) { throw 'Clone the existing repository before publishing changes.' }
git remote -v
git status
Write-Host 'Review changes, then use git add, git commit and git push. This script does not create another repository or automatically publish.'
