puts "Starting LDAP configuration script..."

# Fetch variables from environment (passed via docker-compose from .env)
ldap_host = ENV['LDAP_HOST']
ldap_port = ENV['LDAP_PORT']
ldap_base_dn = ENV['LDAP_BASE_DN']
ldap_bind_dn = ENV['LDAP_BIND_DN']
ldap_password = ENV['LDAP_PASSWORD']
ldap_name = ENV['LDAP_NAME'] || 'OpenLDAP'

# Validate required variables
missing_vars = []
missing_vars << 'LDAP_HOST' if ldap_host.nil? || ldap_host.empty?
missing_vars << 'LDAP_PORT' if ldap_port.nil? || ldap_port.empty?
missing_vars << 'LDAP_BASE_DN' if ldap_base_dn.nil? || ldap_base_dn.empty?
missing_vars << 'LDAP_BIND_DN' if ldap_bind_dn.nil? || ldap_bind_dn.empty?
missing_vars << 'LDAP_PASSWORD' if ldap_password.nil? || ldap_password.empty?

if missing_vars.any?
  puts "Error: Missing required environment variables: #{missing_vars.join(', ')}"
  puts "Ensure these are set in .env and passed in docker-compose.yml"
  exit 1
end

puts "Configuration found:"
puts "  Host: #{ldap_host}"
puts "  Port: #{ldap_port}"
puts "  Base DN: #{ldap_base_dn}"
puts "  Bind DN: #{ldap_bind_dn}"
puts "  Name: #{ldap_name}"

begin
  # Check if table exists (migration check)
  unless AuthSource.connection.table_exists?(:auth_sources)
    puts "AuthSource table not found. Waiting for migration..."
    exit 1
  end

  if AuthSourceLdap.where(name: ldap_name).exists?
    puts "LDAP configuration '#{ldap_name}' already exists. Updating if needed."

    auth = AuthSourceLdap.find_by(name: ldap_name)
    if auth.nil?
      puts "Error: LDAP configuration '#{ldap_name}' not found after exists? check."
      exit 1
    end

    auth.assign_attributes(
      host: ldap_host,
      port: ldap_port.to_i,
      account: ldap_bind_dn,
      account_password: ldap_password,
      base_dn: ldap_base_dn,
      attr_login: 'cn',
      attr_firstname: 'givenName',
      attr_lastname: 'sn',
      attr_mail: 'mail',
      onthefly_register: true,
      tls: false
    )

    if auth.save
      puts "LDAP configuration updated successfully."
    else
      puts "Error updating LDAP configuration: #{auth.errors.full_messages.join(', ')}"
      exit 1
    end
  else
    puts "Creating LDAP configuration '#{ldap_name}'..."
    auth = AuthSourceLdap.new(
      name: ldap_name,
      host: ldap_host,
      port: ldap_port.to_i,
      account: ldap_bind_dn,
      account_password: ldap_password,
      base_dn: ldap_base_dn,
      attr_login: 'cn',
      attr_firstname: 'givenName',
      attr_lastname: 'sn',
      attr_mail: 'mail',
      onthefly_register: true,
      tls: false
    )

    if auth.save
      puts "LDAP configuration created successfully."
    else
      puts "Error creating LDAP configuration: #{auth.errors.full_messages.join(', ')}"
      exit 1
    end
  end

rescue => e
  puts "An error occurred: #{e.message}"
  puts e.backtrace
  exit 1
end
