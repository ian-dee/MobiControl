#!/usr/bin/env ruby
# Supplemental privacy guard, not a replacement for manual data/asset review or secret scanning.
require 'open3'
def git(*args)
  out, status = Open3.capture2e('git', *args)
  abort out unless status.success?
  out
end
root = File.expand_path('..', __dir__)
Dir.chdir(root)
base = '45e3795bad357472c1ff736dbfd7013e574dd28c'
paths = git('ls-files', '-z').split("\0")
failures = []
paths.each do |path|
  next unless File.file?(path)
  if path.match?(%r{(?:\A|/)(?:artifacts|captures|harness|private|\.dependencies|\.idea|\.gradle|build)/}) ||
     path.match?(%r{(?:\A|/)(?:local\.properties|\.env(?:\..*)?|google-services\.json)\z}) ||
     path.match?(/\.(?:apk|aab|db|sqlite|sqlite3|pcap|pcapng|btsnoop|log|zip|jks|p12|pem)\z/i)
    failures << "Disallowed publication path: #{path}"
  end
  data = File.binread(path)
  next unless data.dup.force_encoding('UTF-8').valid_encoding? && !data.include?("\0")
  # Avoid printing a matched value: the finding itself may be sensitive.
  if data.match?(%r{/(?:Users|home)/[A-Za-z0-9_.-]+/}) || data.match?(/-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/)
    failures << "Private path/key pattern: #{path}"
  end
end
git('log', '--format=%H%x09%ae%x09%ce', "#{base}..HEAD").lines.each do |line|
  commit, author, committer = line.strip.split("\t")
  unless [author, committer].all? { |email| email&.end_with?('@users.noreply.github.com') }
    failures << "Review non-no-reply identity on new commit #{commit}"
  end
end
abort failures.join("\n") unless failures.empty?
puts "Publication boundary checks passed for #{paths.size} tracked paths. Manual review remains required."
